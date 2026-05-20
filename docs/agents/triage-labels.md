# Triage Labels

The `triage` skill uses these labels to track issue state:

| Label | Description |
|-------|-------------|
| `needs-triage` | Needs maintainer evaluation |
| `needs-info` | Waiting on reporter for more information |
| `ready-for-agent` | Fully specified, AFK-ready for AI agent |
| `ready-for-human` | Needs human implementation |
| `wontfix` | Will not be actioned |

## State Machine

```
incoming → needs-triage
needs-triage → needs-info (if missing info)
needs-triage → ready-for-agent (if fully specified, automatable)
needs-triage → ready-for-human (if fully specified, needs human)
needs-triage → wontfix (if rejected)
needs-info → needs-triage (after info received)
ready-for-agent → ready-for-human (if agent can't handle)
```
