# Keyboard Shortcuts Reference

**Module:** Python 3 Integration for Ignition 8.3+

Complete keyboard shortcuts for the Python 3 IDE.

---

## Code Editing

| Shortcut | Action | Description |
|----------|--------|-------------|
| **Ctrl + Enter** | Execute Code | Run current Python code |
| **Ctrl + S** | Save Script | Save current script (shows metadata dialog if new) |
| **Ctrl + Shift + S** | Save As | Save script with new name/location |
| **Ctrl + N** | New Script | Create new empty script |
| **Ctrl + O** | Open Script | Load script from tree |
| **Ctrl + W** | Close Script | Close current script (prompts if unsaved) |

---

## Find and Replace

| Shortcut | Action | Description |
|----------|--------|-------------|
| **Ctrl + F** | Find | Open find toolbar |
| **Ctrl + H** | Replace | Open find/replace toolbar |
| **F3** | Find Next | Move to next match |
| **Shift + F3** | Find Previous | Move to previous match |
| **Ctrl + Shift + F** | Find in All Scripts | Search across all saved scripts |

---

## Text Editing

| Shortcut | Action | Description |
|----------|--------|-------------|
| **Ctrl + Z** | Undo | Undo last change |
| **Ctrl + Y** | Redo | Redo last undone change |
| **Ctrl + X** | Cut | Cut selected text |
| **Ctrl + C** | Copy | Copy selected text |
| **Ctrl + V** | Paste | Paste from clipboard |
| **Ctrl + A** | Select All | Select all text in editor |
| **Ctrl + D** | Duplicate Line | Duplicate current line or selection |
| **Ctrl + Shift + D** | Delete Line | Delete current line |
| **Tab** | Indent | Indent selected lines |
| **Shift + Tab** | Unindent | Unindent selected lines |
| **Ctrl + /** | Comment/Uncomment | Toggle line comment |

---

## Font Size

| Shortcut | Action | Description |
|----------|--------|-------------|
| **Ctrl + +** (Plus) | Increase Font | Make editor text larger |
| **Ctrl + -** (Minus) | Decrease Font | Make editor text smaller |
| **Ctrl + 0** (Zero) | Reset Font | Reset to default size (12pt) |

---

## Navigation

| Shortcut | Action | Description |
|----------|--------|-------------|
| **Ctrl + B** | Toggle Sidebar | Show/hide script tree and metadata panels |
| **Ctrl + Shift + P** | Command Palette | Open command palette (VS Code style) |
| **Ctrl + Tab** | Switch Tabs | Cycle through editor tabs |
| **Ctrl + Home** | Go to Start | Jump to beginning of document |
| **Ctrl + End** | Go to End | Jump to end of document |
| **Ctrl + G** | Go to Line | Jump to specific line number |
| **Ctrl + Left** | Previous Word | Move cursor to previous word |
| **Ctrl + Right** | Next Word | Move cursor to next word |

---

## IDE Features

| Shortcut | Action | Description |
|----------|--------|-------------|
| **F5** | Execute Code | Run Python code (same as Ctrl+Enter) |
| **Shift + F5** | Stop Execution | Cancel running code (if supported) |
| **Ctrl + Shift + C** | Clear Output | Clear output and error panels |
| **Ctrl + L** | Clear Terminal | Clear terminal/shell output |
| **Ctrl + R** | Reload Script | Reload current script from disk |
| **F11** | Toggle Fullscreen | Maximize IDE window |

---

## Command Palette (Ctrl+Shift+P)

**Quick access to all IDE commands**

### Execution Commands
- `Execute Code` - Run current Python code
- `Execute in Terminal` - Run as shell command
- `Stop Execution` - Cancel running code

### File Commands
- `New Script` - Create new script
- `Open Script` - Open script from tree
- `Save Script` - Save current script
- `Save Script As` - Save with new name
- `Import Script` - Import from .py file
- `Export Script` - Export to .py file
- `Delete Script` - Remove script permanently

### Search Commands
- `Find` - Open find toolbar
- `Replace` - Open find/replace toolbar
- `Find in Scripts` - Search across all scripts

### View Commands
- `Toggle Sidebar` - Show/hide left panels
- `Clear Output` - Clear output panel
- `Clear Terminal` - Clear terminal panel

### Theme Commands
- `Theme: Dark` - Switch to dark theme
- `Theme: Light` - Switch to light theme
- `Theme: VS Code Dark+` - Switch to VS Code theme

### Gateway Commands
- `Connect to Gateway` - Connect to Ignition Gateway
- `Disconnect` - Disconnect from Gateway
- `Refresh Connection` - Reconnect to Gateway

### Settings Commands
- `Settings` - Open settings dialog
- `Info` - Show module information
- `Packages` - Open package manager

### Tools Commands
- `Keyboard Shortcuts` - Show this reference
- `About Python 3 IDE` - Show version info

### Help Commands
- `Documentation` - Open online docs
- `Report Issue` - Open GitHub issues
- `Check for Updates` - Check for new versions

---

## Context Menu Shortcuts

**Script Tree Right-Click**:
- `L` - Load script
- `E` - Export to file
- `R` - Rename script
- `D` - Delete script
- `M` - Move to folder
- `N` - New folder

**Editor Right-Click**:
- `X` - Cut
- `C` - Copy
- `V` - Paste
- `A` - Select All
- `U` - Undo
- `R` - Redo

---

## Tips & Tricks

### Multiple Selections
- **Ctrl + Click** - Add cursor at click position
- **Ctrl + Shift + Click** - Extend selection to click position

### Block Selection
- **Alt + Shift + Drag** - Select rectangular block of text

### Quick Commands
- **Ctrl + Shift + P** then type to filter commands
- **↑/↓** to navigate, **Enter** to execute
- **Esc** to close

### Auto-Complete
- **Ctrl + Space** - Trigger auto-complete (if available)
- **Tab** - Accept suggestion
- **Esc** - Dismiss suggestions

### Code Folding
- **Ctrl + Shift + [** - Collapse code block
- **Ctrl + Shift + ]** - Expand code block

---

## Customizing Shortcuts

**Note:** Keyboard shortcuts are currently hardcoded. Custom key bindings will be added in a future version.

**Requested in:** [GitHub Issue #XX]

---

## Platform Differences

**macOS Users**: Replace `Ctrl` with `Cmd` (⌘)

| Windows/Linux | macOS | Action |
|---------------|-------|--------|
| Ctrl + S | Cmd + S | Save |
| Ctrl + C | Cmd + C | Copy |
| Ctrl + V | Cmd + V | Paste |
| Ctrl + F | Cmd + F | Find |
| Ctrl + Z | Cmd + Z | Undo |

**All other shortcuts work the same across platforms.**

---

## Accessibility

### Screen Reader Support
- Most keyboard shortcuts work with screen readers
- Tab navigation supported throughout IDE
- ARIA labels on buttons and panels

### High Contrast Themes
- Use Light theme for high contrast
- Font size adjustable (Ctrl + +/-)
- Color themes configurable in settings

---

## Learning the Shortcuts

### Tips for Beginners
1. **Start with basics**: Ctrl+Enter, Ctrl+S, Ctrl+F
2. **Use Command Palette**: Ctrl+Shift+P shows all commands
3. **Learn incrementally**: Add 1-2 shortcuts per week
4. **Print this reference**: Keep handy while learning

### Most Used Shortcuts (Top 10)
1. **Ctrl + Enter** - Execute code
2. **Ctrl + S** - Save script
3. **Ctrl + F** - Find
4. **Ctrl + Z** - Undo
5. **Ctrl + Shift + P** - Command palette
6. **Ctrl + C/V** - Copy/Paste
7. **Ctrl + B** - Toggle sidebar
8. **Ctrl + +/-** - Adjust font size
9. **Ctrl + N** - New script
10. **Tab/Shift+Tab** - Indent/Unindent

---

**Need help?** See [QUICK_START.md](QUICK_START.md) or open an issue on GitHub.
