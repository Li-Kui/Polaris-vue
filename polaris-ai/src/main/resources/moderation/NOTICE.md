# Polaris sensitive-content seed notice

The version-3 seed is a small, curated Polaris derivative of the candidate material from
[houbb/sensitive-word](https://github.com/houbb/sensitive-word), source version
0.29.5, reviewed on 2026-08-13. The source material is licensed under
Apache License 2.0.

Polaris normalized candidate text with Unicode NFKC and case/full-width
normalization, removed duplicates and malformed entries, assigned risk categories
and weights, and added narrowly scoped risk and safe context rules. Version 3 uses
one canonical gambling rule (mechanical Han-internal connectors are normalized in
the runtime matching projection only), atomic resource, address, purchase,
tutorial, download, transaction, and contact contexts, and structural
publicity/identification/prevention safe phrases rather than injectable single-token
safe contexts.
No added rule is independently blocking, and context scoring requires a nearby
risk-word anchor in the same sentence. The six safe
regression phrases are covered by general `SAFE_CONTEXT` rules rather than by
adding their complete sentences as `ALLOW_TERM` rules.

The `sensitive-word-data` runtime dependency is deliberately excluded. The public
dictionary is used only as an offline candidate source; runtime matching loads the
controlled Polaris seed instead.

The seed `checksum` is SHA-256 of the canonical UTF-8 JSON representation of the
`rules` array only. Each rule has exactly the seed schema keys `id`,
`ruleType`, `content`, `normalizedContent`, `category`, and `weight`; canonical
objects emit those keys in fixed ASCII lexicographic order, array order is
retained, Jackson standard JSON string escaping is used, and no insignificant
whitespace is emitted. Seed metadata, including `checksum`, is not part of the
digest. The top-level object and nested `source` object also use closed schemas;
unknown, missing, or duplicate keys and non-integral weight tokens are rejected.
Any schema extension therefore requires an explicit seed format and canonicalizer
update rather than being silently ignored.
