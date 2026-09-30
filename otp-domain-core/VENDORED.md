# VENDORED.md - :otp-domain-core

## 1. Upstream
- URL: https://github.com/opentripplanner/OpenTripPlanner
- Commit: 61a3af6798
- Description: Vendored copy of the domain-core module.

## 2. What was copied
- src/main/java (verbatim)
- src/test/java (verbatim)
- src/main/resources (verbatim)

## 3. Deleted files
None.

## 4. Edited files

### Android runtime compatibility

- `src/main/java/org/opentripplanner/core/model/i18n/LocalizedString.java`:
  - Line 23: `}` escaped in the regex literal (`Pattern.compile("\\{(.*?)}")`
    -> `Pattern.compile("\\{(.*?)\\}")`) because Android's ICU-backed
    `java.util.regex` rejects an unescaped closing brace that OpenJDK
    accepts; found by the on-device ToyNetwork test.

## 5. How to re-sync
1. Delete the current src directories.
2. Copy the trees from OpenTripPlanner/domain-core/.
