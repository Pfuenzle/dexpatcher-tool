# DexWeaver-tool
### Android Dalvik bytecode patcher.

**DexWeaver** is a fork of [DexPatcher-tool](https://github.com/DexPatcher/dexpatcher-tool) by
**Rodrigo Balerdi (Lanchon)** — <https://dexpatcher.github.io/>.

DexWeaver adds **semantic member resolution** to the patch model, so that patches can target
obfuscated members by meaning instead of by their mangled names:

* `@DexFinder` — resolve a class, field or method by a declarative query
  (e.g. `m:public+final, p:com.revenuecat.purchases.CustomerInfo, r:com.example.billing.EntitlementStatus`)
* semantic keys such as `l:` (string literal contained in the method), `f:` (field read by the
  method) and `g:` (getter of a field)
* resolves the obfuscated name of a target and patches the *found* member, keeping its original
  name in the output dex

---

## Attribution

DexWeaver is a **fork** of the following upstream project and is derived from its source code:

| | |
| - | - |
| Upstream project | [DexPatcher/dexpatcher-tool](https://github.com/DexPatcher/dexpatcher-tool) |
| Upstream author | Rodrigo Balerdi ([Lanchon](https://github.com/Lanchon)) |
| Upstream website | <https://dexpatcher.github.io/> |
| License | GNU General Public License v3.0 or later |
| Upstream version forked | `2.0-beta1` (based on `1.8.0-beta1`) |

The upstream project is licensed under the **GNU General Public License, version 3 or later**.
That license is retained in full for this fork — see [`LICENSE.txt`](LICENSE.txt) and
[`NOTICE.txt`](NOTICE.txt). The original copyright headers of the upstream author are preserved
in every source file that was carried over.

Upstream notices that apply to this fork:

* DexPatcher is free software: you can redistribute it and/or modify it under the terms of the
  GNU General Public License as published by the Free Software Foundation, either version 3 of the
  License, or (at your option) any later version.
* DexPatcher is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
  even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
  General Public License for more details.
* You should have received a copy of the GNU General Public License along with DexPatcher. If not,
  see <https://www.gnu.org/licenses/gpl.txt>.

This fork is maintained independently and is not affiliated with or endorsed by the upstream
project.

---

## Packages

The Java packages were renamed from `io.pfuenzle.dexweaver.*` to `io.pfuenzle.dexweaver.*`:

| Upstream | This fork |
| - | - |
| `io.pfuenzle.dexweaver.annotation` | `io.pfuenzle.dexweaver.annotation` |
| `io.pfuenzle.dexweaver` | `io.pfuenzle.dexweaver` |
| `io.pfuenzle.dexweaver.gradle` | `io.pfuenzle.dexweaver.gradle` |

The `multidexlib2` dependency is still the upstream `com.github.lanchon:multidexlib2` artifact.

---

## Usage

### Maven coordinates

Artifacts are published to [JitPack](https://jitpack.io) and can be consumed anonymously:

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.Pfuenzle.dexweaver-tool:dexweaver-annotation:v2.0-beta1'
    implementation 'com.github.Pfuenzle.dexweaver-tool:dexweaver:v2.0-beta1'
}
```

The version is the Git tag name, so it always carries the `v` prefix.

### Annotations

```java
package com.example.billing;

import io.pfuenzle.dexweaver.annotation.DexEdit;
import io.pfuenzle.dexweaver.annotation.DexFinder;
import io.pfuenzle.dexweaver.annotation.DexIgnore;
import io.pfuenzle.dexweaver.annotation.DexReplace;

@DexEdit
@DexFinder("m:public+final, p:com.revenuecat.purchases.CustomerInfo, "
        + "r:com.example.billing.EntitlementStatus")
public final class EntitlementStatus {

    @DexIgnore
    public final boolean a = false;

    @DexReplace
    public final boolean b() {
        return true;
    }
}
```

DexWeaver is free software. (GPLv3+)
