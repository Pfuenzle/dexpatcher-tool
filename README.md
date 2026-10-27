# DexWeaver-tool
### Android Dalvik bytecode patcher.

**DexWeaver** is a fork of [DexPatcher-tool](https://github.com/DexPatcher/dexpatcher-tool) by
**Rodrigo Balerdi (Lanchon)** — <https://dexpatcher.github.io/>.

DexWeaver adds **semantic member resolution** to the patch model, so that patches can target
obfuscated members by meaning instead of by their mangled names:

* `@DexFinder` on a class — locate the class to patch by a query such as
  `m:public+final, p:com.revenuecat.purchases.CustomerInfo, r:…` or simply by its
  fully-qualified name via `className = "…"`.
* `@DexFinder` on a method or field — locate the member inside the patched class by a
  declarative query:
  * `m:` access flags, `p:` parameter types, `r:` return type, `t:` field type, `n:` name
  * `l:` string literal contained in the method body
  * `f:` field read by the method
  * `g:` getter of a named field (method that reads exactly that field and returns it)
* DexWeaver resolves the obfuscated name at patch time and rewrites the output dex so that
  the patched member keeps its original (obfuscated) name.

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

| Upstream | This fork |
| - | - |
| `lanchon.dexpatcher.annotation` | `io.pfuenzle.dexweaver.annotation` |
| `lanchon.dexpatcher` | `io.pfuenzle.dexweaver` |
| `lanchon.dexpatcher.gradle` | `io.pfuenzle.dexweaver.gradle` |

The `multidexlib2` dependency is still the upstream `com.github.lanchon.dexpatcher:multidexlib2`
artifact.

---

## Usage

### Maven coordinates

Artifacts are published to [JitPack](https://jitpack.io) and can be consumed anonymously:

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.Pfuenzle.dexweaver-tool:dexweaver-annotation:v2.0-beta4'
    implementation 'com.github.Pfuenzle.dexweaver-tool:dexweaver:v2.0-beta4'
}
```

The version is the Git tag name, so it always carries the `v` prefix.

### Annotations

The full annotation set mirrors DexPatcher (`@DexEdit`, `@DexIgnore`, `@DexReplace`, `@DexAdd`,
`@DexRemove`, `@DexWrap`, …) plus the new `@DexFinder`.

#### Locating a class and its obfuscated getters

A typical Kotlin data class compiled and obfuscated in an APK loses its field and method names
while keeping a `toString()` that labels the fields (`"isPro="`, `"isPlus="`).  Use
`@DexFinder` to declare how to find the class and its members:

```java
package com.example.billing;

import io.pfuenzle.dexweaver.annotation.DexEdit;
import io.pfuenzle.dexweaver.annotation.DexFinder;
import io.pfuenzle.dexweaver.annotation.DexIgnore;
import io.pfuenzle.dexweaver.annotation.DexReplace;

// Locate the class by its unobfuscated name.
// Alternatively, if only the class name is known: className = "com.example.pkg.ClassName"
@DexEdit
@DexFinder(className = "com.application.example.billing.EntitlementStatus")
public final class EntitlementStatusPatch {

    // Keep the obfuscated fields as-is.
    @DexIgnore public final boolean a = false;
    @DexIgnore public final boolean b = false;
    @DexIgnore public final Object  c = null;
    @DexIgnore public final boolean d = false;

    @DexIgnore
    public EntitlementStatusPatch(boolean isPro, boolean isPlus, Object periodType,
            boolean isFromIos) {}

    // Replace the getter of the "isPro" field.
    // g:<label> means: the method that reads exactly the field whose toString label is <label>.
    @DexFinder("g:isPro=")
    @DexReplace
    public final boolean getter() {
        return true;
    }

    // Replace the getter of "isPlus".
    @DexFinder("g:isPlus=")
    @DexReplace
    public final boolean getter2() {
        return true;
    }

    // Replace the getter of "isFromIos".
    @DexFinder("g:isFromIos=")
    @DexReplace
    public final boolean getter3() {
        return true;
    }
}
```

After patching, the output dex keeps the original obfuscated method names (`a`, `b`, `d`), and
those methods now `return true` instead of reading the field.

#### Finding a method by its signature

```java
@DexEdit
@DexFinder("m:public+final, p:com.revenuecat.purchases.CustomerInfo, "
        + "r:com.example.billing.EntitlementStatus")
public final class EntitlementUtilsPatch {

    @DexReplace
    public final EntitlementStatus theMethod(CustomerInfo customerInfo) {
        return new EntitlementStatus(true, true, null, true);
    }
}
```

The DexWeaver Gradle plugin exposes `checkMappings<Variant>` tasks that validate every
`@DexFinder` directly against the source APK before patching. The checker prints each resolved
class, method, and field, reports every missing target, and exits non-zero when any mapping fails.

#### Finder query keys

| Key | Applies to | Meaning |
| - | - | - |
| `m:` | method, field, class | access flags, e.g. `public+final` |
| `p:` | method | parameter types, comma-separated full class names |
| `r:` | method | return type |
| `t:` | field | field type |
| `n:` | method, field, class | exact member or simple class name |
| `s:` | class | superclass |
| `i:` | class | interface (repeatable with `+`) |
| `a:` | class | annotation present on the class |
| `l:` | method | string literal present in the bytecode |
| `f:` | method, field | field read by the method, or field matched by label |
| `g:` | method | getter of a field (reads exactly that field, no parameters) |
| `z:` | method | minimum instruction count |

---

DexWeaver is free software. (GPLv3+)
