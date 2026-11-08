# Accuracy rules (dimensional output)

These matter because outputs are used to design and print physical parts.

- Never silently clamp, round, or "fix" a measurement. If input is invalid,
  raise.
- Any function that changes geometry must document its unit and its
  assumption (uniform scale, origin, axis convention).
- Report the uncertainty or spread alongside a result when several measurements
  are combined (see `ScaleResult.spread_pct`). Do not hide a poor fit behind
  an average.
- The 1% consistency threshold in `ScaleResult.consistent` is a default, not a
  physical guarantee. Do not change it without saying so in the commit message.
- When adding a new step to the pipeline, state in the docstring how its error
  propagates to final dimensions.
