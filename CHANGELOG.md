# Changelog

## [1.3.0](https://github.com/TurtleOld/mobilemail/compare/v1.2.0...v1.3.0) (2026-06-06)


### Features

* **auth:** revoke OAuth tokens on logout via RFC 7009 ([d706766](https://github.com/TurtleOld/mobilemail/commit/d7067666bfa1e4c0e974d9793fe2bb76001286cc))
* **auth:** revoke OAuth tokens on logout via RFC 7009 ([#20](https://github.com/TurtleOld/mobilemail/issues/20)) ([47af83e](https://github.com/TurtleOld/mobilemail/commit/47af83e1bac96d8f23facb9c355ce45291d443f5))
* **compose:** replace OutlinedTextField with borderless fields and dividers ([ba29f4e](https://github.com/TurtleOld/mobilemail/commit/ba29f4ed9ae7bac517eaf4af3ae908055e097bf1))
* **jmap:** batch Email/query + Email/get with back-reference #ids ([625dbca](https://github.com/TurtleOld/mobilemail/commit/625dbca8b9a46b3569710dacdab22060a5bd2bc5))
* **jmap:** batch Email/query + Email/get with back-reference #ids ([#25](https://github.com/TurtleOld/mobilemail/issues/25)) ([dea65ac](https://github.com/TurtleOld/mobilemail/commit/dea65ac04fc59a63e817ae45fcbc1a958a8b362b))
* **jmap:** batch Email/query + Email/get with back-reference #ids ([#27](https://github.com/TurtleOld/mobilemail/issues/27)) ([3aa8a2e](https://github.com/TurtleOld/mobilemail/commit/3aa8a2ed7882bc8859977ae95da9f965186c87a4))
* **messagedetail:** apply expressive design tokens and add reply/forward bar ([755a0ff](https://github.com/TurtleOld/mobilemail/commit/755a0ff14215dba3d5411ad0c2ad0b29face93ed))
* **messages:** adopt new components and add multi-select read actions ([fce5a8b](https://github.com/TurtleOld/mobilemail/commit/fce5a8b9ccec9e1dcb1205f2b8a2fd44866feaf9))
* **search,outbox:** migrate to standard MaterialTheme tokens ([f063c4a](https://github.com/TurtleOld/mobilemail/commit/f063c4ae10aba76a6441f12f49ab7a58b0a4b106))
* **theme:** migrate to Material 3 Expressive with teal/cyan palette ([15b9dc1](https://github.com/TurtleOld/mobilemail/commit/15b9dc1f06c45c3a0758449a064c2c6dd3e8e720))
* **theme:** migrate to Material 3 Expressive with teal/cyan palette ([#24](https://github.com/TurtleOld/mobilemail/issues/24)) ([cbe6e34](https://github.com/TurtleOld/mobilemail/commit/cbe6e34cddf9f7b20a0991ba0a287355ea24da1e))
* **ui:** add MonogramAvatar and new email list components ([fc41999](https://github.com/TurtleOld/mobilemail/commit/fc419998681434ba6068a98bb4a60a71b8f402b9))


### Bug Fixes

* **auth:** fix OAuthServerMetadata constructor calls and discovery retry logic ([5004dfb](https://github.com/TurtleOld/mobilemail/commit/5004dfb2d56193d4f21a8760c255258f176ee9f7))
* **auth:** replace RuntimeException with IOException in revocation tests ([b73a131](https://github.com/TurtleOld/mobilemail/commit/b73a1310c0507d06e0c80fb8ee0e4ef8add46d52))
* **auth:** validate device_code grant support during OAuth discovery ([68457e4](https://github.com/TurtleOld/mobilemail/commit/68457e491b30d9cf774ba315ebf9ed3b237f5745))
* resolve all compiler warnings ([b8ada92](https://github.com/TurtleOld/mobilemail/commit/b8ada92c0ea70bd815515f0b3f2f7495a3ac5fc7))


### Performance Improvements

* **jmap:** enable HTTP/2 with HTTP/1.1 fallback in JMAP client ([e3d04b4](https://github.com/TurtleOld/mobilemail/commit/e3d04b4f69a7f6650e363003f3ab1201b6bc5c78))
* **jmap:** enable HTTP/2 with HTTP/1.1 fallback in JMAP client ([#23](https://github.com/TurtleOld/mobilemail/issues/23)) ([b0554fa](https://github.com/TurtleOld/mobilemail/commit/b0554fa12509be4bbe67c4655ee8e6c2dd1505e4))


### Refactoring

* **auth:** remove unused OAuth login example ([8c5fd1b](https://github.com/TurtleOld/mobilemail/commit/8c5fd1b312e3b3d8aed170abf39439b50393fdb2))

## [1.2.0](https://github.com/TurtleOld/mobilemail/compare/v1.1.4...v1.2.0) (2026-05-31)


### Features

* **data:** implement domain repository interfaces in data layer ([c138ddb](https://github.com/TurtleOld/mobilemail/commit/c138ddb28e29bd916ca3f1c5f17bf76e40061856))
* **domain:** add domain/model, domain/repository, domain/error layers ([09994ea](https://github.com/TurtleOld/mobilemail/commit/09994ead6e4970db1b9ba961704f83fc41111a9e))
* **domain:** add domain/model, domain/repository, domain/error layers ([7505398](https://github.com/TurtleOld/mobilemail/commit/7505398a66c3f8759f5ab8fc788dccae524ed14c))


### Bug Fixes

* **detekt:** resolve LongParameterList and TooManyFunctions violations ([8774b59](https://github.com/TurtleOld/mobilemail/commit/8774b596913a9a35cdf9bfb945c51e722a7c6827))
* **ui:** inject repositories through view model factories ([6d28a22](https://github.com/TurtleOld/mobilemail/commit/6d28a2299884a08a1f7b80f7c3af9951a19a69ca))
* **ui:** inject repositories through view model factories ([241b6eb](https://github.com/TurtleOld/mobilemail/commit/241b6ebde72a6837648a116b1c62aae8921b3367))


### Refactoring

* **data:** move JMAP mapping logic into repository mappers ([11c82f2](https://github.com/TurtleOld/mobilemail/commit/11c82f272cdaaf7b2c4e3bcabf989e14d795b6ac))
* **data:** move JMAP mapping logic into repository mappers ([27206e8](https://github.com/TurtleOld/mobilemail/commit/27206e8f20bd93b13b95f07d3bd18243a02c67e6))
* **data:** remove redundant *Data wrapper methods and move DateRange to domain ([973bafc](https://github.com/TurtleOld/mobilemail/commit/973bafcf810af7e109943130ba639b424cd51a56))
* **domain:** move Result to domain.common, remove data dependency from interfaces ([5944b54](https://github.com/TurtleOld/mobilemail/commit/5944b54d9ad135527c0251059124362f72f10e42))
* **ui:** migrate UI layer to domain.model types ([e8f332b](https://github.com/TurtleOld/mobilemail/commit/e8f332b8d9d31fffe2b006e94299b390b119975d))

## [1.1.4](https://github.com/TurtleOld/mobilemail/compare/v1.1.3...v1.1.4) (2026-05-31)


### Bug Fixes

* **release:** enable Release Please tag creation and upload APK to existing release ([80179b3](https://github.com/TurtleOld/mobilemail/commit/80179b3c4d24180e30f55cdf89365faa0cae21c4))
* **release:** enable Release Please tag creation and upload APK to existing release ([da1da2f](https://github.com/TurtleOld/mobilemail/commit/da1da2f4caa50107fc4bb8d9601c93e18687b3a5))

## [1.1.3](https://github.com/TurtleOld/mobilemail/compare/v1.1.2...v1.1.3) (2026-05-31)


### Bug Fixes

* **detekt:** resolve all static analysis violations and remove legacy JmapClient ([70b31b2](https://github.com/TurtleOld/mobilemail/commit/70b31b2e97ae9d001af771d5758f3deb172d2acb))
* **detekt:** resolve all static analysis violations and remove legacy JmapClient ([20bea6e](https://github.com/TurtleOld/mobilemail/commit/20bea6ec707ce5e5ee3f9325a350b2bd00f65616))
