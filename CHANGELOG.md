# Changelog

## [1.5.4](https://github.com/TurtleOld/mobilemail/compare/v1.5.3...v1.5.4) (2026-06-24)


### Bug Fixes

* **deps:** update all non-major dependencies ([#66](https://github.com/TurtleOld/mobilemail/issues/66)) ([cf3bf76](https://github.com/TurtleOld/mobilemail/commit/cf3bf761196d78f79f2b6c4915aef8dacb69fc7c))
* **deps:** update compose bom ([9d31d5e](https://github.com/TurtleOld/mobilemail/commit/9d31d5efb7066a7f0e8c98a756e00a8dc54ffdae))
* **deps:** update compose bom ([#67](https://github.com/TurtleOld/mobilemail/issues/67)) ([af89d82](https://github.com/TurtleOld/mobilemail/commit/af89d826bebb45372f075102c0e4cfca6039abf3))
* **deps:** update firebase bom ([#65](https://github.com/TurtleOld/mobilemail/issues/65)) ([2322c8c](https://github.com/TurtleOld/mobilemail/commit/2322c8c0965814a5a1c05a914b68f0b4479321fe))

## [1.5.3](https://github.com/TurtleOld/mobilemail/compare/v1.5.2...v1.5.3) (2026-06-24)


### Bug Fixes

* **message-body:** dark mode for email HTML content ([70d9982](https://github.com/TurtleOld/mobilemail/commit/70d9982356604ebcef15de8d727e9ac8a359cdae))
* **message-body:** dark mode for email HTML content ([#71](https://github.com/TurtleOld/mobilemail/issues/71)) ([404abf9](https://github.com/TurtleOld/mobilemail/commit/404abf9e2c948ac0a98260700f777a1b1d7e5a61))
* **message-body:** shrink-to-fit HTML rendering instead of clipping/laddering ([5413dcf](https://github.com/TurtleOld/mobilemail/commit/5413dcf74c2f9cccca8efc7d5864381bd201cf6b))
* **message-body:** shrink-to-fit HTML rendering instead of clipping/laddering ([#69](https://github.com/TurtleOld/mobilemail/issues/69)) ([06e44bd](https://github.com/TurtleOld/mobilemail/commit/06e44bdb5606b693cb132497debec92ce3c037b7))

## [1.5.2](https://github.com/TurtleOld/mobilemail/compare/v1.5.1...v1.5.2) (2026-06-11)


### Bug Fixes

* **notifications:** run ntfy payload fetch in WorkManager ([5ffc662](https://github.com/TurtleOld/mobilemail/commit/5ffc662df5eef0d5c659ea8e959bee6747c77d87))
* **notifications:** run ntfy payload fetch in WorkManager ([#62](https://github.com/TurtleOld/mobilemail/issues/62)) ([40a5030](https://github.com/TurtleOld/mobilemail/commit/40a5030e43930196a5c2de03c343c287a8f7d6e8))

## [1.5.1](https://github.com/TurtleOld/mobilemail/compare/v1.5.0...v1.5.1) (2026-06-09)


### Bug Fixes

* **oauth:** open browser for device login ([31af414](https://github.com/TurtleOld/mobilemail/commit/31af414c2903046464c6987dbc9b10dc1ef6d9f5))
* **oauth:** share okhttp client and refresh auth ([5ac4178](https://github.com/TurtleOld/mobilemail/commit/5ac4178f739609cf67ca4e6a9e4c3f005c6934a1))
* **oauth:** share okhttp client and refresh auth ([#61](https://github.com/TurtleOld/mobilemail/issues/61)) ([d42d3a8](https://github.com/TurtleOld/mobilemail/commit/d42d3a88d198078656e58b836e7a6eab73cbba91))
* **security:** delay repeated PIN attempts and auto-lock lifecycle ([8e376cc](https://github.com/TurtleOld/mobilemail/commit/8e376cca56de5b6ea84dc84ac5093e8a482f507e))
* **security:** delay repeated PIN attempts and auto-lock lifecycle ([#60](https://github.com/TurtleOld/mobilemail/issues/60)) ([efcee75](https://github.com/TurtleOld/mobilemail/commit/efcee75c6153235bc361871eeca10ed463d67504))
* **webview:** clarify remote content policy ([ec6bcfe](https://github.com/TurtleOld/mobilemail/commit/ec6bcfee9a2136a6d699d94a420868d9e51896b2))
* **webview:** clarify remote content policy ([#58](https://github.com/TurtleOld/mobilemail/issues/58)) ([1e53cda](https://github.com/TurtleOld/mobilemail/commit/1e53cda60a11f1788df6513235db6877d703b966))

## [1.5.0](https://github.com/TurtleOld/mobilemail/compare/v1.4.3...v1.5.0) (2026-06-08)


### Features

* **search:** add offline FTS message search ([f09b7e9](https://github.com/TurtleOld/mobilemail/commit/f09b7e92b6e5146ee25f55312ad45518646d0c0e))


### Bug Fixes

* **deps:** update all non-major dependencies ([4623af0](https://github.com/TurtleOld/mobilemail/commit/4623af084b02cdbec3adf61d0ae3bb36f7239848))
* **deps:** update all non-major dependencies ([#54](https://github.com/TurtleOld/mobilemail/issues/54)) ([99a2517](https://github.com/TurtleOld/mobilemail/commit/99a2517c7fbaf65036310fd9bf4769c7c8fb394f))
* **notifications:** add lock screen privacy modes ([8e39b72](https://github.com/TurtleOld/mobilemail/commit/8e39b7290d93a1966db417dcde659bf1a39180cc))
* **notifications:** add lock screen privacy modes ([#56](https://github.com/TurtleOld/mobilemail/issues/56)) ([cc5243a](https://github.com/TurtleOld/mobilemail/commit/cc5243a6cf072dcdbb0a8bc20d5fc5138f33530d))
* **push:** proxy ntfy polling through backend ([89ad37f](https://github.com/TurtleOld/mobilemail/commit/89ad37fa5247d8d8c6a36288c34aa25b0b060b1a))
* **security:** encrypt Room cache and clear it on logout ([b30cfb1](https://github.com/TurtleOld/mobilemail/commit/b30cfb1a09c1a69165674a37a317d986dfef76a9))
* **sync:** add periodic mail sync fallback ([ceb0323](https://github.com/TurtleOld/mobilemail/commit/ceb0323d988b237b8f22d9ace9a4cf6d77af4d46))
* **sync:** add periodic mail sync fallback ([#55](https://github.com/TurtleOld/mobilemail/issues/55)) ([0a00ce8](https://github.com/TurtleOld/mobilemail/commit/0a00ce8e096149ad5e61c494f459af8164d4fdbc))
* усилить локальный кэш писем и поиск ([#52](https://github.com/TurtleOld/mobilemail/issues/52)) ([713e4d3](https://github.com/TurtleOld/mobilemail/commit/713e4d344afdb99cd818628ba8aa0767e84fa7c7))


### Performance Improvements

* **auth:** add proactive token refresh worker ([2823cb7](https://github.com/TurtleOld/mobilemail/commit/2823cb7abaffa51cc921960c5f4935ddeb9e554c))
* **database:** add message paging indexes ([044a837](https://github.com/TurtleOld/mobilemail/commit/044a8370e238df24522a7d1cc65db91fb7b05f6c))

## [1.4.3](https://github.com/TurtleOld/mobilemail/compare/v1.4.2...v1.4.3) (2026-06-07)


### Bug Fixes

* **messages:** синхронизировать прочтение и удаление писем со Stalwart, добавить отмену удаления ([b41e497](https://github.com/TurtleOld/mobilemail/commit/b41e4975d3c2f5e99bbfd7be197c79e3a9d186fb))
* **messages:** синхронизировать прочтение и удаление писем со Stalwart, добавить отмену удаления ([#50](https://github.com/TurtleOld/mobilemail/issues/50)) ([cb5abfb](https://github.com/TurtleOld/mobilemail/commit/cb5abfbe97ab7280c7ea0f18897d5c51a493ec6d))

## [1.4.2](https://github.com/TurtleOld/mobilemail/compare/v1.4.1...v1.4.2) (2026-06-07)


### Bug Fixes

* **messages:** interleave date headers with messages and fix stuck swipe ([589a4de](https://github.com/TurtleOld/mobilemail/commit/589a4de14273337e0ff2e41978a3ee43607b2b63))
* **messages:** interleave date headers with messages and fix stuck swipe ([#48](https://github.com/TurtleOld/mobilemail/issues/48)) ([71819d5](https://github.com/TurtleOld/mobilemail/commit/71819d5df7e9429c3c6d66b33d4d287e01188152))

## [1.4.1](https://github.com/TurtleOld/mobilemail/compare/v1.4.0...v1.4.1) (2026-06-06)


### Bug Fixes

* **messages:** correct swipe and detail backgrounds ([de23b82](https://github.com/TurtleOld/mobilemail/commit/de23b82344dc74194f687edca03315866e4282d2))
* **messages:** correct swipe and detail backgrounds ([#46](https://github.com/TurtleOld/mobilemail/issues/46)) ([5540dd6](https://github.com/TurtleOld/mobilemail/commit/5540dd6c22346a7817c223548499e081e72c4808))
* **messages:** remove thread detail UI ([58af770](https://github.com/TurtleOld/mobilemail/commit/58af7703acbda667304f7635e8a237ad102d7af2))

## [1.4.0](https://github.com/TurtleOld/mobilemail/compare/v1.3.0...v1.4.0) (2026-06-06)


### Features

* **drawer:** fix folder order and remove unused Queue menu item ([6ac765f](https://github.com/TurtleOld/mobilemail/commit/6ac765f9789953bdb3bfa95908bb4d5728e45984))
* **login:** countdown timer and retry for expired device auth code ([#38](https://github.com/TurtleOld/mobilemail/issues/38)) ([80aa9dc](https://github.com/TurtleOld/mobilemail/commit/80aa9dc1cc3e5ec6d78ce2fbd402ff3b10d451ec))
* **login:** show countdown timer and retry button for expired device code ([8b28332](https://github.com/TurtleOld/mobilemail/commit/8b2833201402b85ea39b321096f3306fffad3139))
* **messagedetail:** compact screen layout ([f7cac7b](https://github.com/TurtleOld/mobilemail/commit/f7cac7bf97571d313e0aa9fed6e00233155e8cb3))
* **messages:** redesign inbox list to flat rows with date section headers ([9ee99f5](https://github.com/TurtleOld/mobilemail/commit/9ee99f5a5d60b0e4b3c122b617baedb9e98c4ad0))
* **notifications:** add notification privacy mode for lock screen ([23a2c08](https://github.com/TurtleOld/mobilemail/commit/23a2c08f3ec93149c34a1b0642cc191dd6c8cf92))
* **notifications:** add notification privacy mode for lock screen ([#36](https://github.com/TurtleOld/mobilemail/issues/36)) ([7060faf](https://github.com/TurtleOld/mobilemail/commit/7060faf2323474ddde423f36eea25251faa71473))
* **settings:** configurable swipe actions per direction ([9a9683a](https://github.com/TurtleOld/mobilemail/commit/9a9683a314a4da28cdc3ea45ffbdf875a7fd47e4))
* **ui:** add auth-expired banner and suppress its snackbar duplicate ([e3eb7de](https://github.com/TurtleOld/mobilemail/commit/e3eb7de99a4ff938922ca2b172777c77c52f9a79))
* **ui:** add offline banner with connectivity monitoring ([e2c4fea](https://github.com/TurtleOld/mobilemail/commit/e2c4fea8877587570f788db5524027a231857e8a))
* **ui:** add pull-to-refresh in messages, search and outbox screens ([90910b6](https://github.com/TurtleOld/mobilemail/commit/90910b657d66118f6f530da766b1583875e78417))
* **ui:** replace initial-load spinners with shimmer skeleton ([90c62b5](https://github.com/TurtleOld/mobilemail/commit/90c62b55cc4a57ea2eadbf49af6f05211c399cd9))
* **ui:** replace manual screenWidthDp check with WindowSizeClass adaptive API ([3ba5551](https://github.com/TurtleOld/mobilemail/commit/3ba5551076c132b9de4839b2a2c89f609713c911))
* **ui:** skeleton loaders, pull-to-refresh, offline/auth banners, adaptive layout ([#30](https://github.com/TurtleOld/mobilemail/issues/30)) ([c8c7cc3](https://github.com/TurtleOld/mobilemail/commit/c8c7cc3e0f63ff0d99484d2be90229201c6b14ba))


### Bug Fixes

* **auth:** clear PIN and Room database on full logout ([f9be432](https://github.com/TurtleOld/mobilemail/commit/f9be432868022c22c62b68d551b2372a19cd01eb))
* **auth:** clear PIN and Room database on full logout ([#35](https://github.com/TurtleOld/mobilemail/issues/35)) ([de44331](https://github.com/TurtleOld/mobilemail/commit/de443310b2fe97b641c2af63685a00f7c09062b2))
* **auth:** prevent double token refresh on concurrent 401 responses ([15a1af7](https://github.com/TurtleOld/mobilemail/commit/15a1af77aa845bf455b4a9c0fb8ddab9930eb6c6))
* **auth:** prevent double token refresh on concurrent 401 responses ([#34](https://github.com/TurtleOld/mobilemail/issues/34)) ([dda552c](https://github.com/TurtleOld/mobilemail/commit/dda552c5757aa5c525d3e2f567458250013a053c))
* **auth:** validate URI scheme before opening OAuth authorization page ([5716f7c](https://github.com/TurtleOld/mobilemail/commit/5716f7c8318d1206002596f256d66ad6973948a9))
* **files:** prevent path traversal in attachment filename handling ([a7f20f3](https://github.com/TurtleOld/mobilemail/commit/a7f20f313606600120dd361c63318950a7f8cee0))
* **messages:** persist mark-as-read on refresh and fallback archive to trash ([4f21f69](https://github.com/TurtleOld/mobilemail/commit/4f21f690f0d2f74fdaa89c2e1188a54d6a5a3078))
* **messages:** persist mark-as-read on refresh and fallback archive to trash ([#28](https://github.com/TurtleOld/mobilemail/issues/28)) ([cb5e128](https://github.com/TurtleOld/mobilemail/commit/cb5e12887cb940b97c097304d1ee025fd63d7ab7))
* **nav:** hide login form during session check, show spinner instead ([f5329f3](https://github.com/TurtleOld/mobilemail/commit/f5329f39f884f6a3df51bae5598ffef698959179))
* **notifications:** validate server URL scheme from incoming intent extras ([1c75b9a](https://github.com/TurtleOld/mobilemail/commit/1c75b9aa30e764d81689add120c7942f1c98cbf2))
* **security:** address URI redirect, path traversal, intent hijacking and timing side-channel ([#32](https://github.com/TurtleOld/mobilemail/issues/32)) ([9cb4600](https://github.com/TurtleOld/mobilemail/commit/9cb46003e73043af375b069773f2ed63351bc7a0))
* **security:** block unsafe URI schemes in WebView (data:, file:, intent://) ([9d9b53b](https://github.com/TurtleOld/mobilemail/commit/9d9b53b640494ee6f638d8eae8ae6d3f8800b4f1))
* **security:** block unsafe URI schemes in WebView (data:, file:, intent://) ([#37](https://github.com/TurtleOld/mobilemail/issues/37)) ([b0f45d0](https://github.com/TurtleOld/mobilemail/commit/b0f45d0e9e1d0086bceeb605089a09317b4f4f37))
* **security:** eliminate timing side-channel in PIN PBKDF2 verification ([589b5d3](https://github.com/TurtleOld/mobilemail/commit/589b5d33d8fa0a85077f1b136b9ad939c1cfe23b))
* **ui:** replace collectAsState with collectAsStateWithLifecycle across all screens ([93ee8ba](https://github.com/TurtleOld/mobilemail/commit/93ee8baf1f672b256969a8ee3d69510da8abee41))
* **vm:** move MailClientFactory.create out of runBlocking into viewModelScope ([9229926](https://github.com/TurtleOld/mobilemail/commit/922992620a724efc2ca73dbaf647b32b95d7addf))
* **vm:** move MailClientFactory.create out of runBlocking into viewModelScope ([#31](https://github.com/TurtleOld/mobilemail/issues/31)) ([22489ae](https://github.com/TurtleOld/mobilemail/commit/22489ae396aa1aaaf63df9d7b80248c3711e4b9c))

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
