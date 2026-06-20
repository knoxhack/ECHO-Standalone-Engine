# Data-only addon example

This module has no Java entrypoint. It can register blocks, items, recipes, and entities from `echo-content/*.json` while remaining `trust: data-only`.

Package the `src/main/resources` directory as a JAR, add the resulting file to a pack manifest, and give the manifest row `trust: data-only`.
