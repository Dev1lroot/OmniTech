# Contributing to OmniTech

Thank you for your interest in contributing. All meaningful contributions are welcome.

---

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Content Policy](#content-policy)
- [How to Contribute](#how-to-contribute)
- [Development Setup](#development-setup)
- [Commit Style](#commit-style)
- [Pull Request Process](#pull-request-process)
- [File Header Requirement](#file-header-requirement)
- [Credit Policy](#credit-policy)

---

## Code of Conduct

Be respectful. Harassment, discrimination, or bad-faith behaviour of any kind will result in removal from the project.

---

## Content Policy

OmniTech is a hard-science mod. Every feature must be grounded in real-world science or engineering. This is not negotiable.

### What is accepted

- Machines, processes, and materials that exist or are actively researched in reality (chemistry, metallurgy, nuclear physics, electronics, aerospace, geology, etc.).
- Improvements to existing assets or mechanics that bring the player closer to how the real process works, or that meaningfully deepen the gameplay of an existing system.
- Speculative or future technology that is a direct extrapolation of current science and is clearly on the research roadmap (e.g. fusion reactors, advanced ion drives, orbital tethers).

### What is not accepted

- **Magic** of any kind — no mana, no arcane energy, no enchantment-adjacent systems.
- **Politics or religion** — the mod takes no stance and introduces no related content.
- **Fictional sci-fi technology** borrowed from movies, TV series, books, or games where it has no real-world scientific basis. Examples that will be rejected outright:
  - Naquadah reactors, Zero Point Modules (*Stargate*)
  - Dilithium crystals, warp cores (*Star Trek*)
  - Hyperdrive, kyber crystals (*Star Wars*)
  - Any named technology from a specific fictional universe

### The placeholder exception

OmniTech covers interstellar travel. Some real enabling technologies (reliable long-duration life support beyond current capability, genuine FTL communication, etc.) do not exist yet. In these specific cases a fictional stand-in **may** be accepted if:

1. No adequate real-world technology or plausible near-future extrapolation exists for that gap.
2. The feature is strictly necessary to make a larger real-science system playable.
3. The item, block, or mechanic is marked in code and in its in-game tooltip with:

   ```
   // TODO: replace with [real technology] when available
   ```

   and documented in its PR description explaining which real technology it stands in for and what research would make it obsolete.

Such placeholders are expected to be removed and replaced as science advances. They are technical debt, not permanent features.

---

## How to Contribute

| Type | Welcome? |
|------|----------|
| Bug fixes | Yes |
| New machines / blocks | Yes, open an issue first |
| New dimensions / planets | Yes, open an issue first |
| Translations | Yes |
| Documentation improvements | Yes |
| Asset replacements | Ask first — assets have a deliberate style |
| Dependency upgrades | Only if there is a concrete reason |

For anything non-trivial, **open an issue before writing code** so we can agree on direction and avoid wasted effort.

---

## Development Setup

**Requirements**

- Java 25 (Temurin recommended)
- Gradle 8+ (wrapper included — use `./gradlew`)
- IntelliJ IDEA or VS Code with the Java extension

**Steps**

```bash
git clone https://github.com/Dev1lroot/OmniTech.git
cd OmniTech
./gradlew build          # compile + package
./gradlew runClient      # launch game client for manual testing
./gradlew runData        # regenerate models / lang files
./gradlew compileJava    # fast compile check without full build
```

Decompiled NeoForge and Minecraft sources are placed under `decompiled/` during the first build and are gitignored.

---

## Commit Style

Write the subject line in factual past tense — describe what happened, not what to do. Keep it under 72 characters. Multiple independent changes in one commit may be separated by a semicolon:

```
Ganymede planet dimension added
Reactor heat bar client sync fixed
ReactorBlock renamed to NuclearReactorBlock
Fuel Rod durability changed from 200 to 2000; Herobrine removed
```

No ticket numbers in commit messages — use the PR description for context.

---

## Pull Request Process

1. Fork the repository and create a branch from `master`.
2. Make your changes. Add or update tests where they exist.
3. Ensure `./gradlew build` passes with no errors.
4. **Test in-game** — follow the checklist below before opening the PR.
5. Open a pull request against `master`. Fill in the PR template.
6. Respond to review comments. The maintainer may request changes before merging.

PRs that add features without a prior issue discussion may be closed without review.

### In-Game Testing Checklist

A passing build proves the code compiles. It does not prove the feature works. Before submitting, launch the game with `./gradlew runClient` and go through every applicable step:

**Basic smoke test (required for every PR)**
- [ ] Create a brand-new world (do not reuse an existing test world).
- [ ] Place every block or item added or modified by this PR.
- [ ] Interact with each one through its full intended use — fill a tank, run a recipe, power a machine, fire the rocket, etc.
- [ ] Save and exit the world fully (this flushes chunk data and saved-data files to disk).
- [ ] Re-open the same world and verify that all placed blocks and their state (inventory, energy, fluid, NBT) loaded back correctly.

**For world-generation changes**
- [ ] Generate several chunks in the relevant biome/dimension and confirm the feature spawns at the expected rate and shape.
- [ ] Confirm it does not corrupt or overwrite adjacent features (caves, structures, other ores).
- [ ] Test with a fresh seed you have never loaded before.

**For GUI / screen changes**
- [ ] Open every affected screen on both a local and a dedicated server (`./gradlew runServer`) and verify layout and data sync are correct.
- [ ] Resize the game window while a screen is open and confirm nothing overflows or misaligns.

**For networking / packet changes**
- [ ] Test client ↔ server round-trip on a locally hosted dedicated server, not just in single-player (which skips most of the packet path).

**For changes to chunk loading or block entity ticking**
- [ ] Travel far enough to unload the chunks containing your blocks, then travel back.
- [ ] Confirm the block entity resumed ticking correctly after chunk reload.
- [ ] Check the server log for any errors during unload and reload.

If a step is not applicable to your PR, note that explicitly in the PR description rather than silently skipping it.

---

## File Header Requirement

All Java source files must carry a GPL-3.0-only header. The copyright lines reflect authorship — the license never changes.

**New file created by a contributor:**  
Put your name on top, as the primary author of that file.

```java
/*
 * Copyright (c) 2026 Jane Doe <jane@example.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
```

**Existing file modified by a contributor:**  
Add your name below the existing copyright line(s).

```java
/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * Copyright (c) 2026 Jane Doe <jane@example.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
```

Files missing a header or missing the `SPDX-License-Identifier` line will not be merged.

---

## Credit Policy

Any contributor whose pull request is merged will be added to [CREDITS.md](CREDITS.md) with their name, role, and optional contact. "Meaningful" means a code, asset, documentation, or translation change that is accepted and shipped — trivial fixes (typos, whitespace) may or may not be credited at the maintainer's discretion.

By submitting a pull request you confirm that:

- You wrote the code yourself, or have the right to contribute it.
- You license your contribution to the project under **GPL-3.0-only**.
- The maintainer retains the right to accept, reject, or modify submissions.
