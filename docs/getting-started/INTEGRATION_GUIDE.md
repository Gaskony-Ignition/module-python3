# Integration Guide: Authoring and Calling Python 3 Scripts

**Audience:** Ignition Designer developers who want to write a Python 3
script once and call it from real project surfaces (Perspective, tag
scripts, gateway events).

This guide is task-oriented. For the security model behind it, see
`SECURITY.md` and `docs/PROJECT_CHARTER.md` §2. For installing the module
itself, see [QUICK_START.md](QUICK_START.md).

---

## 1. Concept

Ignition is built on Jython 2.7 and, realistically, always will be — but
nearly every Python developer today works in Python 3, and the packages
that matter (`requests`, `pandas`, `numpy`, …) are Python 3 only. This
module makes Python 3 feel like a native part of the Gateway: Python 3
scripts live on the Gateway the same way a Project Library does for
Jython. You author and test them in the Designer — via the Project
Browser's **"Python 3 Scripts"** node and the **Script Console** — and then
call them from anywhere Jython runs (Perspective pages, tag change scripts,
gateway timer/event scripts) through `system.python3.*`, exactly as you
already call a Project Library function.

---

## 2. Author and test in the Designer

### Create a script via the Project Browser

1. Open the Designer and find the **"Python 3 Scripts"** node in the
   Project Browser (same tree as your Project Library, Perspective views,
   etc.).
2. Right-click the node (or a folder inside it) and choose **New
   Script...**.
3. Give the script a name. It's created with a starter template:

   ```python
   # MyFirstScript
   # Press Ctrl+Enter to run

   print('Hello from MyFirstScript')
   ```

4. Organise scripts into folders the same way you would in a Project
   Library: right-click → **New Folder...**, then drag scripts into it.
   Folder paths become part of the script's address — a script named
   `Daily Summary` inside a folder called `Reports` is called as
   `"Reports/Daily Summary"`.
5. Save with **Ctrl+S**.

### Test in the Script Console

- Open **Tools → Python 3 Script Console** from the Designer menu bar.
- Write or paste code into the editor and press **Ctrl+Enter** to run it
  on the Gateway. Output and errors (with a full Python traceback) appear
  in the console panel below.
- The Script Console is a scratchpad — use it to iterate on logic before
  saving it as a script in the Project Browser, and to sanity-check a
  saved script's behaviour with sample inputs before wiring it into a
  Perspective binding or event script.
- Both the Project Browser and the Script Console talk to the Gateway over
  an authenticated Designer session — no gateway configuration is required
  for this to work (see [Security](#4-security) if you get an error here).

---

## 3. Call from projects

Every call site below uses `system.python3.callScript`, `exec`, `eval`, or
`callModule` — the same functions, whichever Jython context you're in.

### Perspective: button `onActionPerformed`

Say you've saved a script called `Calc Tax` in a `Finance` folder that
expects a positional `amount` and a keyword `rate`, and sets `result`:

```python
# Finance/Calc Tax (saved Python 3 script)
amount = args[0]
rate = kwargs.get("rate", 0.10)
result = round(amount * (1 + rate), 2)
```

Wire it to a Perspective button's `onActionPerformed` event (Jython):

```python
def runAction(self, event):
    amount = self.getSibling("AmountInput").props.value
    total = system.python3.callScript(
        "Finance/Calc Tax", [amount], {"rate": 0.08}
    )
    self.getSibling("TotalLabel").props.text = "Total: ${:.2f}".format(total)
```

### Tag change script

```python
# Tag Change script on a pressure PV
def valueChanged(tag, tagPath, previousValue, currentValue, initialChange,
                  missedEvents):
    if not currentValue.good:
        return
    psi = currentValue.value

    # A saved Python 3 script that flags out-of-range readings using a
    # rolling-average model too fiddly to write in Jython comfortably.
    alert = system.python3.callScript("Monitoring/Pressure Anomaly Check",
                                       [psi])
    if alert:
        system.tag.writeBlocking(["[default]Alarms/PressureAnomaly"], [True])
```

### Gateway timer script

```python
# Project > Gateway Events > Timer Scripts (e.g. every 60000 ms)
def execute():
    readings = system.tag.readBlocking([
        "[default]Line1/FlowRate", "[default]Line2/FlowRate"
    ])
    values = [r.value for r in readings]

    # Offload the statistics to Python 3 -- e.g. because you want numpy/pandas
    report = system.python3.callScript("Reports/Daily Flow Summary", [values])
    logger = system.util.getLogger("FlowSummary")
    logger.info(str(report))
```

### Quick reference: `exec` / `eval` / `callModule`

These are the same functions the Script Console uses; call them from any
project script too.

| Function | Use for | Signature |
| --- | --- | --- |
| `system.python3.exec(code, variables={})` | Statements: assignments, imports, loops, multi-line code. Set a `result` variable to return a value. | `code: String`, `variables: Dict` (optional) |
| `system.python3.eval(expression, variables={})` | A single expression whose value you want back directly — no `result =` needed. | `expression: String`, `variables: Dict` (optional) |
| `system.python3.callModule(moduleName, functionName, args, kwargs={})` | Calling exactly one function from a module without writing `exec()` boilerplate. | `moduleName: String`, `functionName: String`, `args: List`, `kwargs: Dict` (optional) |
| `system.python3.callScript(scriptPath, args=[], kwargs={})` | Running a saved script by name/folder path — the pattern used above. | `scriptPath: String`, `args: List` (optional), `kwargs: Dict` (optional) |

```python
# exec -- multi-line code, returns whatever you assign to 'result'
area = system.python3.exec(
    "import math\nresult = math.pi * radius**2", {"radius": 5.0}
)

# eval -- a single expression
total = system.python3.eval("x + y", {"x": 10, "y": 20})

# callModule -- one function call, no exec() boilerplate
root = system.python3.callModule("math", "sqrt", [16])  # 4.0
```

Notes on parameters that apply to all four:

- `variables` (`exec`/`eval`) and `kwargs` (`callModule`/`callScript`) are
  plain dicts — keys become Python names, values are converted
  automatically (`String`, `int`, `float`, `bool`, `list`, `dict`, `None`).
- `exec` and `eval` each have an overload that also accepts a Python
  version string (e.g. `"3.11"`) as a trailing argument, for Gateways with
  more than one Python version installed; omit it to use the Gateway
  administrator's configured default. `callModule` and `callScript` do not
  take a version argument — they always run on the default pool.
- Every call is synchronous and blocks the calling thread until the
  Gateway's Python subprocess pool returns a result — in Perspective, call
  from `onActionPerformed`/event scripts rather than inside a binding's
  hot render path if the script does anything non-trivial.

---

## 4. Security

### Runtime scripting is on by default

`system.python3.*` calls from project Jython (tag scripts, gateway events,
Perspective bindings) are **allowed by default** — this matches Jython's
own trust level: a project script that wanted to cause harm could already
do so directly in Jython, so Python 3 is neither more nor less gated.

A Gateway administrator can disable `system.python3.*` fleet-wide, for
example to shrink the Python supply-chain surface, by setting either:

- the system property `ignition.python3.scriptingFunctions.allowed=false`, or
- the environment variable `IGNITION_PYTHON3_SCRIPTING_ALLOWED=false`

When disabled, every `system.python3.*` call from project Jython fails
with:

```text
Python 3 scripting functions (system.python3.*) have been disabled by the
gateway administrator (ignition.python3.scriptingFunctions.allowed=false).
```

This opt-out only affects the runtime scripting path. It does **not**
affect your ability to author and test scripts in the Designer — an
authenticated Designer session can always develop and run Python 3 code,
independent of this setting (see `SECURITY.md`).

### Never feed end-user input into `exec`/`eval` — treat it like SQL injection

`system.python3.exec` and `system.python3.eval` run whatever Python source
you hand them, with full capabilities. Building that source out of
end-user or page input is the Python equivalent of building a SQL query by
string-concatenating user input — don't do it.

**Bad — do not do this:**

```python
# Perspective text field bound to self.custom.userExpression, fed straight
# into eval(). Any Designer-savvy visitor can type
# "__import__('subprocess').run(['rm','-rf','/'])" into the field.
userExpression = self.getSibling("ExpressionInput").props.text
result = system.python3.eval(userExpression)
```

**Good — author a saved script, pass typed arguments:**

```python
# Finance/Calc Tax (saved Python 3 script, authored and reviewed by a
# Designer developer -- not user-supplied code)
amount = args[0]
rate = kwargs.get("rate", 0.10)
result = round(amount * (1 + rate), 2)
```

```python
# Perspective event script -- end-user input only ever flows in as a typed
# argument, never as code
amount = self.getSibling("AmountInput").props.value
total = system.python3.callScript("Finance/Calc Tax", [amount], {"rate": 0.08})
```

The end user controls `amount`, a number — never the Python source that
runs. That's the boundary: author the logic once as a saved script, then
call it with data, the same discipline used for parameterised SQL queries.

---

## 5. Environment: Python versions and packages

Which Python versions are installed and which packages (via `pip`) are
available is controlled by a **Gateway administrator**, in the Gateway web
UI (Config → Python 3 Integration). This is the module's supply-chain
control point.

From the Designer, you can see this environment — which Python versions and
packages are currently available, so you know what you can `import` — but
the Gateway web UI is the intended surface of record for actually
installing or removing a package or Python version
(`docs/PROJECT_CHARTER.md` §3). If `import pandas` fails with
`ModuleNotFoundError`, ask your Gateway administrator to install it via the
Gateway web UI (Config → Python 3 Integration → Packages) rather than
scripting a `pip install` around it.

---

## See also

- [QUICK_START.md](QUICK_START.md) — installing the module itself
- `SECURITY.md` — full trust model and REST API authentication
- `docs/PROJECT_CHARTER.md` §2–3 — personas, trust model, and surface ownership
- `docs/api/DESIGNER_IDE.md` — full Designer IDE reference
- `docs/api/REST_API.md` — remote/external execution via REST
