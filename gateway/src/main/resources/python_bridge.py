#!/usr/bin/env python3
"""
Python Bridge Script for Ignition Python 3 Integration
This script runs as a persistent subprocess and handles JSON-RPC style commands.
"""

import sys
import json
import traceback
import importlib
import io
import contextlib
import os
import ast
from typing import Any, Dict

# Resource limits (configured via environment variables)
# Memory limit: 2048MB default (can be overridden with PYTHON3_MAX_MEMORY_MB)
# CPU time limit: 60 seconds default (can be overridden with PYTHON3_MAX_CPU_SECONDS)
#
# v4.4.0: default raised 512 -> 2048. RLIMIT_AS caps *virtual* address space,
# not resident memory; OpenBLAS (numpy/pandas/scipy/matplotlib) reserves large
# virtual arenas per thread that far exceed real RSS, so the old 512MB cap made
# `import numpy` fail outright with "Memory allocation failed" — i.e. the
# flagship use case of this module was unusable. The parent process also sets
# OPENBLAS_NUM_THREADS / MALLOC_ARENA_MAX to keep that virtual reservation close
# to actual usage (see Python3Executor). The cap still stops a runaway script
# from exhausting the gateway; tune via -Dignition.python3.max.memory.mb.
MAX_MEMORY_MB = int(os.environ.get('PYTHON3_MAX_MEMORY_MB', '2048'))
MAX_CPU_SECONDS = int(os.environ.get('PYTHON3_MAX_CPU_SECONDS', '60'))

# Apply resource limits (Unix/Linux and Windows)
try:
    import platform
    import resource

    # Set memory limit (virtual address space). See the note above on why this is
    # 2GB by default and paired with thread/arena caps from the parent process.
    max_memory_bytes = MAX_MEMORY_MB * 1024 * 1024
    resource.setrlimit(resource.RLIMIT_AS, (max_memory_bytes, max_memory_bytes))
    print(f"Resource limit applied: Max memory = {MAX_MEMORY_MB}MB", file=sys.stderr)

    # Set CPU time limit (prevents runaway CPU-intensive scripts)
    resource.setrlimit(resource.RLIMIT_CPU, (MAX_CPU_SECONDS, MAX_CPU_SECONDS))
    print(f"Resource limit applied: Max CPU time = {MAX_CPU_SECONDS}s", file=sys.stderr)

except ImportError:
    # Windows doesn't have resource module - use Job Objects (v2.9.0 - HIGH-05 fix)
    if platform.system() == 'Windows':
        try:
            import ctypes
            from ctypes import wintypes

            # Windows Job Object API constants
            JOB_OBJECT_LIMIT_PROCESS_MEMORY = 0x00000100
            JOB_OBJECT_LIMIT_JOB_MEMORY = 0x00000200
            JOB_OBJECT_LIMIT_PROCESS_TIME = 0x00000002

            # Load Windows kernel32.dll
            kernel32 = ctypes.windll.kernel32

            # Create Job Object
            job = kernel32.CreateJobObjectW(None, None)
            if not job:
                raise OSError("Failed to create Windows Job Object")

            # Assign current process to Job Object
            hProcess = kernel32.GetCurrentProcess()
            if not kernel32.AssignProcessToJobObject(job, hProcess):
                raise OSError("Failed to assign process to Job Object")

            # Define JOBOBJECT_BASIC_LIMIT_INFORMATION structure
            class JOBOBJECT_BASIC_LIMIT_INFORMATION(ctypes.Structure):
                _fields_ = [
                    ('PerProcessUserTimeLimit', wintypes.LARGE_INTEGER),
                    ('PerJobUserTimeLimit', wintypes.LARGE_INTEGER),
                    ('LimitFlags', wintypes.DWORD),
                    ('MinimumWorkingSetSize', ctypes.c_size_t),
                    ('MaximumWorkingSetSize', ctypes.c_size_t),
                    ('ActiveProcessLimit', wintypes.DWORD),
                    ('Affinity', ctypes.POINTER(wintypes.ULONG)),
                    ('PriorityClass', wintypes.DWORD),
                    ('SchedulingClass', wintypes.DWORD),
                ]

            # Define JOBOBJECT_EXTENDED_LIMIT_INFORMATION structure
            class JOBOBJECT_EXTENDED_LIMIT_INFORMATION(ctypes.Structure):
                _fields_ = [
                    ('BasicLimitInformation', JOBOBJECT_BASIC_LIMIT_INFORMATION),
                    ('IoInfo', wintypes.LARGE_INTEGER * 2),  # IO_COUNTERS
                    ('ProcessMemoryLimit', ctypes.c_size_t),
                    ('JobMemoryLimit', ctypes.c_size_t),
                    ('PeakProcessMemoryUsed', ctypes.c_size_t),
                    ('PeakJobMemoryUsed', ctypes.c_size_t),
                ]

            # Set up extended limit information
            extendedInfo = JOBOBJECT_EXTENDED_LIMIT_INFORMATION()

            # Set memory limit (process memory)
            extendedInfo.ProcessMemoryLimit = max_memory_bytes
            extendedInfo.BasicLimitInformation.LimitFlags = JOB_OBJECT_LIMIT_PROCESS_MEMORY

            # Set CPU time limit (in 100-nanosecond intervals)
            cpu_time_100ns = MAX_CPU_SECONDS * 10_000_000  # Convert seconds to 100ns units
            extendedInfo.BasicLimitInformation.PerProcessUserTimeLimit = cpu_time_100ns
            extendedInfo.BasicLimitInformation.LimitFlags |= JOB_OBJECT_LIMIT_PROCESS_TIME

            # Apply limits to Job Object
            JobObjectExtendedLimitInformation = 9  # Constant for extended limit info class
            if not kernel32.SetInformationJobObject(
                    job,
                    JobObjectExtendedLimitInformation,
                    ctypes.byref(extendedInfo),
                    ctypes.sizeof(extendedInfo)):
                raise OSError("Failed to set Job Object limits")

            print(f"Windows Job Object resource limits applied: Max memory = {MAX_MEMORY_MB}MB, Max CPU time = {MAX_CPU_SECONDS}s", file=sys.stderr)

        except Exception as e:
            print(f"WARNING: Failed to apply Windows resource limits: {e}", file=sys.stderr)
            print("Resource limits not enforced on Windows.", file=sys.stderr)
    else:
        print("WARNING: resource module not available. Resource limits not applied.", file=sys.stderr)

except Exception as e:
    # Non-fatal: log error but continue
    print(f"WARNING: Failed to apply resource limits: {e}", file=sys.stderr)


class PythonBridge:
    """Handles communication between Java and Python 3.

    Trust model (May 2026, security review C13):
    --------------------------------------------
    The previous "RESTRICTED"/"ADMIN"/"DESIGNER_ADMIN" sandbox modes were
    removed because the AST validation and string-match checks were trivially
    bypassable via classic CPython escape vectors
    (``[].__class__.__mro__[1].__subclasses__()``,
    ``getattr(__builtins__, 'ev'+'al')`` etc.). The bridge now executes
    Python source unfiltered. The Gateway-side Java entry-points
    (``system.python3.exec``, ``Python3RestEndpoints``) are responsible for
    enforcing access control; this script trusts whatever it receives.

    For real isolation between users and Gateway-host privileges, deploy the
    Gateway in a container or VM whose blast radius matches your trust
    requirements -- the OS-level isolation is the actual security boundary.

    The ``security_mode`` field is still accepted in the JSON wire protocol
    for backward compatibility with older clients, but it is ignored: every
    request runs with full Python 3 capabilities.
    """

    def __init__(self):
        self.globals_dict = {}
        self.version = sys.version

    def execute_code(self, code: str, variables: Dict[str, Any] = None, security_mode: str = None) -> Dict[str, Any]:
        """Execute Python code with full capabilities.

        ``security_mode`` is accepted but ignored -- see class docstring (C13).
        Access control happens on the Java side before reaching this script.
        """
        try:
            # Merge provided variables with globals
            exec_globals = self.globals_dict.copy()
            if variables:
                exec_globals.update(variables)

            # Capture stdout during execution
            stdout_capture = io.StringIO()

            with contextlib.redirect_stdout(stdout_capture):
                # Execute code with full Python 3 capabilities. The Gateway-side
                # role check is the actual security boundary -- not this layer.
                # Use exec_globals as both globals and locals so functions can see each other
                exec(code, exec_globals, exec_globals)

            # Get captured output
            captured_output = stdout_capture.getvalue()

            # Return the 'result' variable if it exists, otherwise return captured output
            result = exec_globals.get('result', captured_output if captured_output else None)

            return {
                'success': True,
                'result': self._serialize(result),
                'output': captured_output if captured_output else None
            }

        except Exception as e:
            return {
                'success': False,
                'error': f"{type(e).__name__}: {str(e)}",  # Include exception type
                'traceback': traceback.format_exc()
            }

    def evaluate_expression(self, expression: str, variables: Dict[str, Any] = None, security_mode: str = None) -> Dict[str, Any]:
        """Evaluate a Python expression with full capabilities.

        ``security_mode`` is accepted but ignored -- see class docstring (C13).
        """
        try:
            # Merge provided variables with globals
            eval_globals = self.globals_dict.copy()
            if variables:
                eval_globals.update(variables)

            # Evaluate expression with full Python 3 capabilities. The Gateway-side
            # role check is the actual security boundary -- not this layer.
            result = eval(expression, eval_globals)

            return {
                'success': True,
                'result': self._serialize(result)
            }

        except Exception as e:
            return {
                'success': False,
                'error': f"{type(e).__name__}: {str(e)}",  # Include exception type
                'traceback': traceback.format_exc()
            }

    def call_module(self, module_name: str, function_name: str, args: list = None, kwargs: dict = None, security_mode: str = None) -> Dict[str, Any]:
        """Import a module and call a function with full capabilities.

        ``security_mode`` is accepted but ignored -- see class docstring (C13).
        """
        try:
            # Import module using the standard mechanism. The Gateway-side
            # role check is the actual security boundary -- not this layer.
            module = importlib.import_module(module_name)

            # Get function
            if not hasattr(module, function_name):
                raise AttributeError(f"Module '{module_name}' has no attribute '{function_name}'")

            func = getattr(module, function_name)

            # Call function
            args = args or []
            kwargs = kwargs or {}
            result = func(*args, **kwargs)

            return {
                'success': True,
                'result': self._serialize(result)
            }

        except Exception as e:
            return {
                'success': False,
                'error': f"{type(e).__name__}: {str(e)}",  # Include exception type
                'traceback': traceback.format_exc()
            }

    def get_version(self) -> Dict[str, Any]:
        """Get Python version information"""
        return {
            'success': True,
            'result': {
                'version': sys.version,
                'version_info': {
                    'major': sys.version_info.major,
                    'minor': sys.version_info.minor,
                    'micro': sys.version_info.micro
                },
                'executable': sys.executable,
                'platform': sys.platform
            }
        }

    def list_modules(self) -> Dict[str, Any]:
        """List installed modules"""
        try:
            import pkg_resources
            installed = [pkg.key for pkg in pkg_resources.working_set]
            installed.sort()

            return {
                'success': True,
                'result': installed
            }
        except Exception as e:
            return {
                'success': False,
                'error': f"{type(e).__name__}: {str(e)}",  # Include exception type
                'traceback': traceback.format_exc()
            }

    def clear_globals(self) -> Dict[str, Any]:
        """Clear global variables"""
        self.globals_dict.clear()
        return {
            'success': True,
            'result': 'Globals cleared'
        }

    def check_syntax(self, code: str) -> Dict[str, Any]:
        """Check Python code for syntax errors using AST and pyflakes"""
        try:
            import ast
            errors = []

            # First, check for syntax errors using AST
            try:
                ast.parse(code)
            except SyntaxError as e:
                errors.append({
                    'line': e.lineno if e.lineno else 1,
                    'column': e.offset if e.offset else 0,
                    'message': str(e.msg) if e.msg else str(e),
                    'severity': 'error'
                })

            # If no syntax errors, try pyflakes for additional checks
            if not errors:
                try:
                    import pyflakes.api
                    import pyflakes.reporter

                    # Capture pyflakes warnings
                    warning_stream = io.StringIO()
                    error_stream = io.StringIO()

                    # Custom reporter to capture warnings
                    reporter = pyflakes.reporter.Reporter(warning_stream, error_stream)
                    pyflakes.api.check(code, '<string>', reporter=reporter)

                    # Parse pyflakes output
                    warnings = warning_stream.getvalue()
                    if warnings:
                        for line in warnings.strip().split('\n'):
                            if line:
                                # Format: <string>:line:col: message
                                parts = line.split(':', 3)
                                if len(parts) >= 4:
                                    try:
                                        line_num = int(parts[1])
                                        col_num = int(parts[2]) if parts[2].strip().isdigit() else 0
                                        message = parts[3].strip()
                                        errors.append({
                                            'line': line_num,
                                            'column': col_num,
                                            'message': message,
                                            'severity': 'warning'
                                        })
                                    except (ValueError, IndexError):
                                        pass

                except ImportError:
                    # pyflakes not installed, skip additional checks
                    pass
                except Exception:
                    # Don't fail if pyflakes check fails
                    pass

            return {
                'success': True,
                'result': {'errors': errors}
            }

        except Exception as e:
            return {
                'success': False,
                'error': f"{type(e).__name__}: {str(e)}",  # Include exception type
                'traceback': traceback.format_exc()
            }

    def get_completions(self, code: str, line: int, column: int) -> Dict[str, Any]:
        """Get code completions at cursor position using Jedi"""
        try:
            try:
                import jedi
            except ImportError:
                # Jedi not installed, return fallback completions
                return {
                    'success': True,
                    'result': {
                        'completions': [],
                        'message': 'Jedi library not installed. Install with: pip install jedi'
                    }
                }

            # Create Jedi script for analysis
            script = jedi.Script(code, path='<stdin>')

            # Get completions at cursor position (Jedi uses 1-based line numbers)
            completions = script.complete(line, column)

            # Format completion results
            completion_list = []
            for completion in completions[:50]:  # Limit to 50 results
                try:
                    completion_item = {
                        'text': completion.name,
                        'type': completion.type,  # 'function', 'class', 'module', 'keyword', etc.
                        'complete': completion.complete,  # Full completion text
                    }

                    # Add description if available
                    try:
                        if completion.docstring():
                            # Extract first line of docstring for summary
                            doc_lines = completion.docstring().split('\n')
                            summary = doc_lines[0] if doc_lines else ''
                            completion_item['description'] = summary[:100]  # Limit description
                            completion_item['docstring'] = completion.docstring()[:500]  # Full docstring (limited)
                    except Exception:
                        pass

                    # Add function signature if available
                    try:
                        if completion.type in ('function', 'class'):
                            signatures = completion.get_signatures()
                            if signatures:
                                sig = signatures[0]
                                params = []
                                for param in sig.params:
                                    param_str = param.name
                                    if param.infer_default():
                                        try:
                                            default_val = str(param.infer_default()[0].name)
                                            param_str += f'={default_val}'
                                        except Exception:
                                            pass
                                    params.append(param_str)
                                completion_item['signature'] = f"{completion.name}({', '.join(params)})"
                    except Exception:
                        pass

                    completion_list.append(completion_item)

                except Exception:
                    # Skip this completion if there's an error
                    continue

            return {
                'success': True,
                'result': {
                    'completions': completion_list,
                    'count': len(completion_list)
                }
            }

        except Exception as e:
            return {
                'success': False,
                'error': f"{type(e).__name__}: {str(e)}",  # Include exception type
                'traceback': traceback.format_exc()
            }

    # REMOVED in v2.9.0: execute_shell() method (SECURITY FIX: HIGH-02)
    # Arbitrary shell command execution with shell=True was a critical security vulnerability.
    # This feature allowed command injection attacks and has been permanently removed.
    # For safe subprocess execution, use the 'execute' command with Python's subprocess module directly.

    def _serialize(self, obj: Any) -> Any:
        """Convert Python objects to JSON-serializable format"""
        if obj is None:
            return None
        elif isinstance(obj, (bool, int, float, str)):
            return obj
        elif isinstance(obj, (list, tuple)):
            return [self._serialize(item) for item in obj]
        elif isinstance(obj, dict):
            return {str(k): self._serialize(v) for k, v in obj.items()}
        elif isinstance(obj, set):
            return list(obj)
        elif isinstance(obj, bytes):
            return obj.decode('utf-8', errors='replace')
        else:
            # For objects, try to convert to string
            return str(obj)

    def process_request(self, request: Dict[str, Any]) -> Dict[str, Any]:
        """Process a request and return response"""
        command = request.get('command')

        # ``security_mode`` is accepted for protocol back-compat but ignored
        # by the bridge -- access control is enforced on the Java side
        # (RoleResolver.requireAdministrator) before reaching this script
        # (security review C13, May 2026).
        security_mode = request.get('security_mode', None)

        if command == 'execute':
            return self.execute_code(
                request.get('code', ''),
                request.get('variables'),
                security_mode
            )

        elif command == 'evaluate':
            return self.evaluate_expression(
                request.get('expression', ''),
                request.get('variables'),
                security_mode
            )

        elif command == 'call_module':
            return self.call_module(
                request.get('module'),
                request.get('function'),
                request.get('args'),
                request.get('kwargs'),
                security_mode
            )

        elif command == 'version':
            return self.get_version()

        elif command == 'list_modules':
            return self.list_modules()

        elif command == 'clear_globals':
            return self.clear_globals()

        elif command == 'check_syntax':
            return self.check_syntax(request.get('code', ''))

        elif command == 'get_completions':
            return self.get_completions(
                request.get('code', ''),
                request.get('line', 1),
                request.get('column', 0)
            )

        elif command == 'execute_shell':
            # REMOVED in v2.9.0 for security reasons (HIGH-02)
            return {
                'success': False,
                'error': 'execute_shell command was permanently disabled in v2.9.0 for security reasons',
                'traceback': ''
            }

        elif command == 'ping':
            return {'success': True, 'result': 'pong'}

        else:
            return {
                'success': False,
                'error': f"Unknown command: {command}"
            }

    def run(self):
        """Main loop: read requests from stdin, write responses to stdout"""
        # Signal ready
        sys.stdout.write(json.dumps({'status': 'ready'}) + '\n')
        sys.stdout.flush()

        while True:
            try:
                # Read request (line-based JSON)
                line = sys.stdin.readline()

                if not line:
                    # EOF - exit gracefully
                    break

                # Parse request
                request = json.loads(line.strip())

                # Check for shutdown command
                if request.get('command') == 'shutdown':
                    sys.stdout.write(json.dumps({'success': True, 'result': 'shutting down'}) + '\n')
                    sys.stdout.flush()
                    break

                # Process request
                response = self.process_request(request)

                # Write response
                sys.stdout.write(json.dumps(response) + '\n')
                sys.stdout.flush()

            except json.JSONDecodeError as e:
                error_response = {
                    'success': False,
                    'error': f"JSON decode error: {str(e)}"
                }
                sys.stdout.write(json.dumps(error_response) + '\n')
                sys.stdout.flush()

            except Exception as e:
                error_response = {
                    'success': False,
                    'error': f"Unexpected error: {str(e)}",
                    'traceback': traceback.format_exc()
                }
                sys.stdout.write(json.dumps(error_response) + '\n')
                sys.stdout.flush()


if __name__ == '__main__':
    bridge = PythonBridge()
    bridge.run()
