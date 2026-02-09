---
name: Release
about: List of steps for release process
title: Release A.B.C
labels: ''
assignees: ''

---

Like https://github.com/metafacture/metafacture-core/issues/697.

The maven central release will be the first semi-automatical, see https://github.com/metafacture/metafacture-core/issues/709

This release follows  7.0.0 https://github.com/metafacture/metafacture-core/issues/697

Following [maintaining guidelines](https://github.com/metafacture/metafacture-core/blob/master/MAINTAINING.md).

- [ ] update dependencies if these are promising to not break much
- [ ] follow maintainer guidelines and upload release to maven central for testing
- [ ] test uploaded release from maven central
- [ ] [release on maven central](https://central.sonatype.com/search?q=metafacture)
- [ ] [release on github](https://github.com/metafacture/metafacture-core/releases/tag/metafacture-core-7.0.0)
- [ ]  update  [metafacture-playground](https://github.com/metafacture/metafacture-playground/issues/221)
- [ ] update [flux-commands](https://github.com/metafacture/metafacture-documentation/blob/master/docs/Documentation-Maintainer-Guide.md)
- [ ] update [metafacture-fix' functions](https://github.com/metafacture/metafacture-documentation/blob/master/docs/fix/Fix-functions.md
- [ ] write [blog post](https://github.com/metafacture/metafacture-blog/issues/39)
- [ ] [toot](https://openbiblio.social/@metafacture/) 
- [ ] announce at [metadaten.community](https://metadaten.community/c/software-und-tools/metafacture/8)
