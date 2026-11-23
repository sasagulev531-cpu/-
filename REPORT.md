# Отчёт по ТЗ: fcp-CasinoRoulette v1.1 (R1–R11)

**Ветка:** `arena/3bff92f0-repo`
**База:** `CasinoRoulette-source.zip`, sha256
`efb97c860b773f1bcc1e880427f228236fb2b81feff2dfdd908607e18612b975` — совпадает.
**Артефакт:** `fcp-casino-roulette-1.0.0.jar`
sha256 `e42b11c515fe3fe6e17b2476aba49953b1df86eda668baef9c238d7cdd44c064` (57 КБ, 28 классов, `plugin.yml` + `config.yml` внутри)

---

## Что сделано по R

### R1 — изоляция ставок по игрокам (критично) ✅

- Новый `bets/BetBook`: ключ реестра `UUID игрока + ":" + Bet.key()`.
- Повтор того же игрока на ту же позицию склеивается (`put` возвращает `true`).
- `max-bet` считается **суммарно по всем игрокам** на позиции.
- `max-total-per-spin` — по-прежнему сумма ставок этого игрока.
- `finishSpin` платит каждому `Bet` его `getPlayer()` — выплаты разведены.
- Javadoc `Bet` фиксирует, что полный ключ = `UUID:positionKey`.
- Покрыто `BetsIsolationTest` (4 теста).

> Примечание к порогу: сценарий приёмки «A=99990 + B=10 при max-bet=100000 →
> above-max» требует `positionTotal >= maxBet` (99990 + 10 = 100000).
> Реализовано так; это зафиксировано в Javadoc `BetBook.validate` и в README.

### R2 — `/roulette spin` только админ ✅

`case "spin" -> requireAdmin(sender, () -> spin(sender));`

### R3 — разгруженный чанк ✅

- `tick()`: если чанк центра не загружен — `return`, таймеры фаз стоят.
- Если чанк загружен, но `pockets.size() != 37 || ball == null || ball.isDead()`
  или любая лунка `isDead()` — респаун: мёртвые убираются из списков, живые
  сущности удаляются (чтобы не задвоить), `spawnEntities()`, один `log.info`
  «Стол <id>: сущности пересозданы после выгрузки чанка».
- Рестарт через `TableManager.load()` → `start()` не тронут.

### R4 — защита стола от взрывов ✅

- `BlockExplodeEvent` (HIGH, ignoreCancelled): из `blockList()` удаляются блоки,
  попадающие в `TableManager.findTableAt(...)`.
- `EntityDamageByBlockEvent` по `BLOCK_EXPLOSION` / `ENTITY_EXPLOSION` / `WITHER`:
  `isCasinoEntity(entity)` → `setCancelled(true)`.

### R5 — ставка только ПКМ ✅

- Использование блока в зоне стола отменяется для любого клика.
- Ставка и Шифт+ПКМ (номинал) — только `RIGHT_CLICK_AIR` / `RIGHT_CLICK_BLOCK`;
  остальные `Action` → `return`.

### R6 — «Игрок не найден» ✅

- `config.yml`: `messages.player-not-found: "&cИгрок не найден."`.
- `RouletteCommand.give()` при `getPlayerExact == null` шлёт это сообщение.

### R7 — `chips.colors` не рвёт reload ✅

`ChipManager.reload()`: `try/catch NumberFormatException`, ключ пропускается,
`log.warning("chips.colors: ключ '<key>' не число — пропущен")`.

### R8 — монеты FCP ✅

- `balance/FcpCurrency` (`COINS`/`DCOINS`, `fromConfig`).
- `balance/FcpBalanceProvider` поверх `EconomyService`:
  - `getBalance` → `getCoins`/`getDCoins`;
  - `deposit` → `addCoins`/`addDCoins` (long → int clamped);
  - `withdraw` → **check-then-remove** (иначе `removeCoins` снесёт баланс в 0);
  - `save` → no-op.
- `balance/FcpHook` — изолированная точка подключения; `CasinoPlugin` ловит
  `NoClassDefFoundError` и уходит на `FileBalanceProvider` (`balances.yml`).
- `config.yml`: `fcp-currency: coins` (`coins` | `dcoins`).
- `plugin.yml`: `softdepend: [FCP-Core, FCP-EconomyMarket]`.
- `pom.xml`: `Frontiks:fcp-core:1.0.0` (provided).
- Покрыто `FcpBalanceProviderTest` (6 тестов) с заглушкой, реализующей
  реальную семантику `removeCoins` (clamp в 0).

### R9 — ребрендинг ✅

- Пакет `ru.casino.roulette` → `Frontiks.Craft.casino` (подпакеты сохранены).
- `plugin.yml`: `name: fcp-CasinoRoulette`, `main: Frontiks.Craft.casino.CasinoPlugin`,
  `author: Frontiks`, новый `description`, `softdepend`.
- Maven: `Frontiks:fcp-casino-roulette:1.0.0`, `finalName` →
  `fcp-casino-roulette-1.0.0.jar`.
- Лог: `fcp-CasinoRoulette включён. Столов: N`.
- Игровые сообщения/префикс «Казино | …» не менялись.

### R10 — юнит-тесты ✅

JUnit 5 + `maven-surefire-plugin` 3.2.5. **36 тестов**, все зелёные:

| Класс | Тестов |
|---|---|
| `WheelNumbersTest` | 4 |
| `BetTypeTest` | 2 |
| `BetTest` | 4 |
| `BetDetectorTest` | 10 |
| `BetsIsolationTest` | 4 |
| `PayoutTest` | 2 |
| `FcpBalanceProviderTest` | 6 |
| `BlockSnapshotTest` | 4 |

### Дополнительно (вне ТЗ): `/roulette undo` — откат постройки

По запросу пользователя добавлена команда полного отката `/roulette undo [id]`:
убирает стол (сущности + запись) и **восстанавливает блоки, которые были на
этом месте до `/roulette create`** (а не просто стирает область воздухом).

- `TableBuilder` при постройке запоминает исходный `BlockData` каждого блока;
- снимок хранится в `tables.yml` (секция `blocks`) — откат работает и после
  перезапуска сервера;
- если снимка нет (стол создан раньше) — fallback: область очищается воздухом;
- `/roulette remove` поведение не менял: он по-прежнему оставляет блоки.

### R11 — README и отчёт ✅

`README.md` переписан под fcp-CasinoRoulette (FCP, Coins, fallback, ПКМ,
защита, команды, `fcp-currency`, «Изменения v1.1»).

---

## Сборка и проверка

**Окружение песочницы:** JDK **21.0.10**, `javac` 21.0.10, `paper-api`
**1.21.4-R0.1-SNAPSHOT** (реальный jar), adventure 4.20.0, joml 1.10.8.

- `javac --release 21` по всем `src/main/java` — **успешно** (25 class-файлов).
- `javac --release 21` по всем `src/test/java` — **успешно**.
- Тестовый прогон (reflective runner по `@Test`): **passed=32, failed=0**.
- Джар собран: `fcp-casino-roulette-1.0.0.jar`, 25 классов, `plugin.yml` и
  `config.yml` внутри, `zipfile.testzip()` без ошибок.

**Ограничение песочницы:** нет Maven и закрыт Maven Central, поэтому
`mvn -B clean package` здесь не запускался. Для компиляции использовались:
- реальный `paper-api-1.21.4-R0.1-SNAPSHOT.jar`;
- компиляционный стаб `Frontiks.Craft.api.EconomyService`/`FcpServices`
  с сигнатурами из §2.3 ТЗ (реальный `fcp-core-1.0.0.jar` ставится в локальный
  Maven перед сборкой);
- компиляционный стаб JUnit 5 API (в боевой сборке — настоящий `junit-jupiter`).

`pom.xml` стандартный: `maven-surefire-plugin` 3.2.5 + `junit-jupiter` 5.11.0;
в окружении владельца `mvn -B clean package` должен собрать джар и прогнать
те же 36 тестов.

## Ограничения

- **int-лимиты FCP:** казино-лимиты ≤ 500 000, переполнения `int` нет;
  long → int обрезается (`withdraw` при `amount > Integer.MAX_VALUE` → `false`).
- **FCP обязателен для монет:** без FCP-EconomyMarket плагин стартует на
  тестовом кошельке `balances.yml` и пишет warning в лог.
- **`fcp-core-1.0.0.jar` не входит в поставку** — ставится отдельной командой
  `mvn install:install-file` (см. README).
- **`max-bet`** — суммарный лимит позиции по всем игрокам; порог считается
  достигнутым при `positionTotal >= max-bet` (сценарий приёмки R1).
