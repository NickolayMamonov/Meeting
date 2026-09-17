# Changelog

All notable Android releases are documented here.

## [1.1.0](https://github.com/NickolayMamonov/Meeting/compare/v1.0.0...v1.1.0) (2026-08-28)


### Features

* **release:** simplify Android release assets ([fa9b476](https://github.com/NickolayMamonov/Meeting/commit/fa9b476bdf186b51631dccb3fc4ac6b251eafd87))
* **release:** simplify Android release assets ([1666f86](https://github.com/NickolayMamonov/Meeting/commit/1666f86c4dc2ed30d15ec5e61f74190fd16abfaa))


### Bug Fixes

* **release:** accept generated version state ([1670aa6](https://github.com/NickolayMamonov/Meeting/commit/1670aa6b9a415c7638c9b5b348d9ecd991b736c8))
* **release:** accept generated version state ([8bdfb08](https://github.com/NickolayMamonov/Meeting/commit/8bdfb088db38783fc0163d264309f689f0e816ff))
* **release:** accept verified self-signed AABs ([a3df98e](https://github.com/NickolayMamonov/Meeting/commit/a3df98e73fd97770c826fc5c7d4e37ffaaa491c0))
* **release:** bind attestations to workflow commit ([e913b9a](https://github.com/NickolayMamonov/Meeting/commit/e913b9aa9eade2d24c3a1dd1003fdcff2c7725a1))
* **release:** bind QA attestations to current runs ([c13b0e3](https://github.com/NickolayMamonov/Meeting/commit/c13b0e3f18aabe6a09d35d4da57c6c03f7b7b046))
* **release:** bind QA identity fixtures and audit counts ([b589c13](https://github.com/NickolayMamonov/Meeting/commit/b589c13eb9a8354af2619e91c0a3f1c5fff90665))
* **release:** close exact publication identity gaps ([c084206](https://github.com/NickolayMamonov/Meeting/commit/c0842069a0bb0964506797ca06a1869d7d66d58f))
* **release:** close protected publication QA gaps ([e229e90](https://github.com/NickolayMamonov/Meeting/commit/e229e906a5bf57260bccc388ee67ad4785381edd))
* **release:** derive cmdline package identity ([82ad035](https://github.com/NickolayMamonov/Meeting/commit/82ad0350111d6eeca4cfc4871306b70460265143))
* **release:** pin post-build tooling to workflow commit ([502ea75](https://github.com/NickolayMamonov/Meeting/commit/502ea7551db5607aeef732c360b207d0ddbe2787))
* **release:** resolve Android SDK tools before signing ([e1ac654](https://github.com/NickolayMamonov/Meeting/commit/e1ac6542a015d047751dfe0c3a7f3f6c0e72492b))
* **release:** resolve drafts from paginated releases ([7b4641a](https://github.com/NickolayMamonov/Meeting/commit/7b4641ae3676fcb24c1d6ed648b132614e89c343))
* **release:** resolve Windows apkanalyzer launcher ([87b0166](https://github.com/NickolayMamonov/Meeting/commit/87b0166b89c045e64cc15d16e42a4e550499ee3f))
* **release:** resolve Windows apkanalyzer launcher ([92311fa](https://github.com/NickolayMamonov/Meeting/commit/92311fa21e7b16cfa74ac75de7790beb9e014873))
* **release:** resume canonical Android draft safely ([5518008](https://github.com/NickolayMamonov/Meeting/commit/551800898da092d5857d91c0ec51b873c6174cf1))
* **release:** use list authority for fresh drafts ([3a61300](https://github.com/NickolayMamonov/Meeting/commit/3a61300a95c3f0e7181eccdd8b59d3c007a194b1))
* **release:** validate cmdline metadata identity ([385f6ec](https://github.com/NickolayMamonov/Meeting/commit/385f6eccc8f7f82657db2901b50afa2a13e93a05))
* **release:** validate cmdline metadata identity ([1d970d3](https://github.com/NickolayMamonov/Meeting/commit/1d970d35304f9ff33fc432cfdb8e081f935d15a8))
* **release:** validate cmdline package path ([fce49d7](https://github.com/NickolayMamonov/Meeting/commit/fce49d7bacf5be801583b5675138b22a04fcdd96))
* **release:** validate stable Firebase config early ([a06fbf6](https://github.com/NickolayMamonov/Meeting/commit/a06fbf60d9b70c34d336014450f2410e53e1cf30))

## 1.0.0 (2026-08-15)


### Bug Fixes

* **release:** bind package subjects to statements ([0c26f2f](https://github.com/NickolayMamonov/Meeting/commit/0c26f2f06b8503f794e7d5db014ec82307e60869))
* **release:** bind package subjects to statements ([6f267bc](https://github.com/NickolayMamonov/Meeting/commit/6f267bc61fd97061e17977e4618df0b5edb49f64))
* **release:** compare authoritative DSSE envelopes ([dd8d3ab](https://github.com/NickolayMamonov/Meeting/commit/dd8d3ab5a5b60ed89e11456cc0e2d822dbd139e8))
* **release:** correct Release Please JSON updater ([4a9224d](https://github.com/NickolayMamonov/Meeting/commit/4a9224db01df3bb1b42126beb8e0492e29eb6794))
* **release:** extend apkanalyzer cold timeout ([#63](https://github.com/NickolayMamonov/Meeting/issues/63)) ([9af62dc](https://github.com/NickolayMamonov/Meeting/commit/9af62dc6d1b55595f783635fdd0bd20a10871e39))
* **release:** harden Android signing bootstrap ([#56](https://github.com/NickolayMamonov/Meeting/issues/56)) ([7aec34b](https://github.com/NickolayMamonov/Meeting/commit/7aec34b8dd27c4bf2de68bcbee86ebfdf48cb059))
* **release:** make APK debuggability explicit ([#61](https://github.com/NickolayMamonov/Meeting/issues/61)) ([7855d6e](https://github.com/NickolayMamonov/Meeting/commit/7855d6eaa4c8962ff3cf5d926d3484c8ecff7b80))
* **release:** reject conflicting authoritative sources ([0d30769](https://github.com/NickolayMamonov/Meeting/commit/0d30769fff49642c09a524d9a0bc633ddcb844fa))
* **release:** remove Android pinning contract ([c6aaf7a](https://github.com/NickolayMamonov/Meeting/commit/c6aaf7aa49673bd4a118b09aa31ecfa745d73604))
* **release:** repair credential audit workflow ([967aa62](https://github.com/NickolayMamonov/Meeting/commit/967aa6248c3cad0476e7b0b91cdaaec21d1ce1b5))
* **release:** repair credential audit workflow ([595899e](https://github.com/NickolayMamonov/Meeting/commit/595899e23c74ff702c73ea98ddb4268a456af653))
* **release:** repair live Android signing bootstrap ([#57](https://github.com/NickolayMamonov/Meeting/issues/57)) ([34bf009](https://github.com/NickolayMamonov/Meeting/commit/34bf009e75edae1e679b830a8929246141b2e593))
* **release:** resolve snapshot apkanalyzer path ([#60](https://github.com/NickolayMamonov/Meeting/issues/60)) ([6d75325](https://github.com/NickolayMamonov/Meeting/commit/6d75325106833f9a391a651713f6c4bdb85b9cd2))
* **release:** resolve snapshot apksigner deterministically ([#59](https://github.com/NickolayMamonov/Meeting/issues/59)) ([bf27fd3](https://github.com/NickolayMamonov/Meeting/commit/bf27fd36195528fd46dd84009edfb846c1d16586))
* **release:** support multi-subject Rekor evidence ([2790573](https://github.com/NickolayMamonov/Meeting/commit/2790573780f692e4f50529c07023098853582ff4))
* **release:** use bundle certificate metadata ([5ea2167](https://github.com/NickolayMamonov/Meeting/commit/5ea2167a93f9b9eb80a812636016cf236bd8de5f))
* **release:** validate authoritative attestation bundles ([25111ba](https://github.com/NickolayMamonov/Meeting/commit/25111bab21e3f618e75471ceb6c060ed5b7dc988))

## [1.0.0] - Unreleased

- Initial Android production release line.
