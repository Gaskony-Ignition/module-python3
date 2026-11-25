# Known issues

### Scripts written for pandas 2

The `datascience` bundle installs pandas 3 (3.1.1 since 4.6.5; Linux gateways
have pulled pandas 3 from PyPI since its release). Installed packages are kept
across module upgrades, so a gateway changes only when the bundle is installed
again. These pandas 2 patterns behave differently, checked on a gateway against
pandas 2.3.3:

| Pandas 2 script | Under pandas 3 | Change it to |
| --- | --- | --- |
| `resample("H")`, `freq="H"`, `"30T"`, `"S"` | `ValueError: Invalid frequency` | `"h"`, `"30min"`, `"s"` |
| `resample("M")` (month end) | `ValueError` | `"ME"` (also `"QE"`, `"YE"`) |
| `df["col"][0] = x` | Silently does nothing (copy-on-write) | `df.loc[0, "col"] = x` |
| `df["col"].fillna(0, inplace=True)` | Silently does nothing | `df["col"] = df["col"].fillna(0)` |
| Writing to a column you pulled out (`s = df["col"]; s.iloc[0] = x`) | `df` is unchanged | Write through `df.loc` / `df.iloc` |
| `df["tag"].dtype == object` for text | `False`; text columns are dtype `str` | `pd.api.types.is_string_dtype(...)` |
| Missing text value is `None` | It is `NaN`, so a result shows `NaN`, not null | `df.astype(object).where(df.notna(), None)` before returning |
| `datetime64[ns]` assumed | New datetimes are `datetime64[us]` | Don't hard-code the unit |

Group-bys, rolling windows, pivots, merges, `concat`, `describe`, CSV/JSON
round trips, `to_dict`, string methods and matplotlib output behave the same.

### Audit log never records caller identity

`Python3SecurityUtils.getCurrentUser()`/`getSourceIP()` are unimplemented
stubs that always return `null`, so every audit event is written with
`user=UNAUTHENTICATED`, `sourceIP=LOCAL` regardless of who authenticated. Not
an authorisation bypass — the role gates are separate code and still enforce
— but per-user attribution is unavailable from the audit trail alone. See
[SECURITY.md](../SECURITY.md).

### Autocomplete fails silently when `jedi` is missing

If a Python distribution's venv has no `jedi` installed,
`Python3CompletionProvider` returns zero completions and the popup never
appears — no error in the Designer, no user-facing warning; the only
evidence is `ModuleNotFoundError: No module named 'jedi'` in the gateway log.
`jedi` should be installed as part of provisioning a Python version, and a
missing dependency should surface as a visible "completions unavailable"
state rather than silence.

### Audit log concatenates source lines without a separator

`Action=PYTHON_CHECK_SYNTAX, Details=import pandas as pdpd.` is
`import pandas as pd` followed by `pd.`, run together with no separator.
Cosmetic, but it is the audit trail, which should be readable literally.
