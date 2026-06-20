# Module Authoring

## API dependency

Build against:

```text
dist/echo-engine-api-2.0.0-beta.2.jar
```

Do not compile a module against runtime implementation packages. Public contracts are under `dev.echo.engine.api`.

## Descriptor

Place this at `META-INF/echo.module.json`:

```json
{
  "schemaVersion": 1,
  "id": "examplemodule",
  "name": "Example Module",
  "version": "1.0.0",
  "entrypoint": "example.module.ExampleModule",
  "trust": "developer",
  "dependencies": [
    {"id": "somebase", "version": "1.0.0", "optional": false}
  ],
  "permissions": ["content.register", "gameplay.extend"]
}
```

For a data-only addon:

```json
{
  "schemaVersion": 1,
  "id": "exampledata",
  "name": "Example Data Addon",
  "version": "1.0.0",
  "entrypoint": "",
  "trust": "data-only",
  "dependencies": [],
  "permissions": ["content.register"]
}
```

## Blocks

`echo-content/blocks.json`:

```json
[
  {
    "id": "exampledata:signal_glass",
    "displayName": "Signal Glass",
    "color": "#55C8E8",
    "solid": true,
    "opaque": false,
    "hardness": 0.4,
    "hazardPerSecond": 0,
    "emittedLight": 9,
    "properties": {
      "drop": "exampledata:signal_glass"
    }
  }
]
```

## Items

`echo-content/items.json`:

```json
[
  {
    "id": "exampledata:signal_glass",
    "displayName": "Signal Glass",
    "maxStack": 64,
    "placesBlock": "exampledata:signal_glass"
  }
]
```

Food and water items can add `foodRestore` and `hydrationRestore`.

## Recipes

`echo-content/recipes.json`:

```json
[
  {
    "id": "exampledata:craft_signal_glass",
    "ingredients": {
      "exampledata:signal_dust": 4
    },
    "result": "exampledata:signal_glass",
    "resultCount": 2
  }
]
```

## Entities

`echo-content/entities.json`:

```json
[
  {
    "id": "exampledata:signal_wraith",
    "displayName": "Signal Wraith",
    "color": "#66BBDD",
    "maxHealth": 14,
    "moveSpeed": 2.5,
    "hostile": true,
    "spawnWeight": 3,
    "properties": {
      "drop": "exampledata:signal_dust"
    }
  }
]
```

## Code entrypoint

```java
public final class ExampleModule implements EchoModule {
    @Override
    public void onLoad(EchoModuleContext context) {
        context.registries().worldGenerators().register(
            ResourceId.parse("examplemodule:worldgen"),
            new WorldGeneratorDefinition(
                ResourceId.parse("examplemodule:worldgen"),
                100,
                new ExampleWorldGenerator()
            )
        );

        context.services().register(GameExtension.class, new ExampleGameExtension());
    }
}
```

`GameExtension` supports session startup, fixed-step ticking, block interactions, and module HUD lines. Persistent values belong in `game.state(moduleId)`.

## Pack row

```json
{
  "id": "examplemodule",
  "version": "1.0.0",
  "file": "modules/examplemodule-1.0.0-standalone.jar",
  "sha256": "<64 lowercase hex characters>",
  "required": true,
  "trust": "developer"
}
```

Both descriptor trust and pack trust must permit code execution.
