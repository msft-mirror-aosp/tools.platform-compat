# Platform compat tools

Tools for Android App Compat (go/android-compat).

## What's in here?

<!--
# Get the dir structure:
$ tree -F --dirsfirst -d --prune  tools/platform-compat/annotation/
-->

```
annotation
├── processors # Java annotation processors for annotations defined in src/
│   ├── changeid # Creates compat_config xml files from @ChangeId constants
│   └── unsupportedappusage # Creates csv files with source positions for @UnsupportedAppUsage elements
└── src # Source files for annotation themselves 
```

