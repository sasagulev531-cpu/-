# fcp-CasinoRoulette — как это работает

Это подробное объяснение реализации: где лежат файлы, как устроен плагин,
что именно было сломано и как починено (R1–R11), и как всё проверить.

> Короткая версия: `README.md` (установка/команды), `REPORT.md` (отчёт по пунктам ТЗ),
> `SPEC.md` — само техническое задание.

---

## 1. Где лежит работа

Всё — в корне репозитория `/home/user/-`:

```
.
├── fcp-casino-roulette-1.0.0.jar   ← ГОТОВЫЙ ПЛАГИН (положить в plugins/)
├── pom.xml                         ← Maven-сборка (Frontiks:fcp-casino-roulette:1.0.0)
├── src/main/java/Frontiks/Craft/casino/
│   ├── CasinoPlugin.java           ← точка входа: подключение валюты FCP, регистрация
│   ├── balance/                    ← валюта: FcpBalanceProvider, FileBalanceProvider, FcpHook
│   ├── bets/                       ← ставки: Bet, BetBook, BetType, BetDetector, Payout
│   ├── table/                      ← стол: RouletteTable (фазы/анимация), TableBuilder, TableManager
│   ├── listeners/TableListener.java← клики, антигриф, взрывы
│   ├── commands/RouletteCommand.java ← /roulette
│   └── util/                       ← WheelNumbers, ChipManager, Messages
├── src/main/resources/
│   ├── plugin.yml                  ← имя fcp-CasinoRoulette, softdepend FCP-Core/FCP-EconomyMarket
│   └── config.yml                  ← лимиты, фишки, сообщения (обратно совместим)
├── src/test/java/...               ← 32 юнит-теста JUnit 5
├── README.md                       ← установка, команды, ставки
├── REPORT.md                       ← отчёт по R1–R11
└── HOW_IT_WORKS.md                 ← этот файл
```

---

## 2. Общая архитектура

```
                ┌─────────────────────────────────────────────┐
                │              CasinoPlugin (onEnable)         │
                │  1. saveDefaultConfig, Messages.init         │
                │  2. Валюта: FcpHook.create() ──┐             │
                │     нет FCP → FileBalanceProvider│            │
                │  3. TableManager.load() — столы из tables.yml│
                │  4. registerEvents(TableListener)            │
                │  5. /roulette → RouletteCommand              │
                └───────────────┬─────────────────────────────┘
                                │
        ┌───────────────────────┼──────────────────────────────┐
        ▼                       ▼                              ▼
 ┌─────────────┐        ┌──────────────┐              ┌────────────────┐
 │TableManager │ 1..*   │ RouletteTable│  (тикер 20/с)│  TableListener  │
 │ tables.yml  │───────▶│ phase, bets  │◀─────────────│ PlayerInteract  │
 │ findTableAt │        │ pockets/ball │   tryPlaceBet│ BlockExplode    │
 └─────────────┘        │ displays     │              │ EntityDamage    │
                        └──────┬───────┘              └────────────────┘
                               │ использует
          ┌────────────────────┼──────────────────────┐
          ▼                    ▼                      ▼
   ┌────────────┐      ┌──────────────┐      ┌──────────────────┐
   │  BetBook   │      │ BetDetector  │      │ BalanceProvider  │
   │ UUID:позиция│     │ клик → тип+ │      │ FcpBalanceProvider│
   │ + лимиты   │      │  числа       │      │ (или File…fallback)│
   └────────────┘      └──────────────┘      └──────────────────┘
```

**Ключевая идея:** физический стол — это `RouletteTable`, у которого есть таймер
(`tick()` каждый тик). Ставки лежат в `BetBook` (реестр), деньги ходят через
`BalanceProvider` (абстракция над FCP), а клик игрока превращается в ставку
в `BetDetector`.

---

## 3. Жизненный цикл ставки (главный сценарий)

### Шаг 1. Игрок кликает ПКМ по столу
`TableListener.onInteract` (`listeners/TableListener.java`):

- берутся только клики **правой кнопкой** (`RIGHT_CLICK_AIR`/`RIGHT_CLICK_BLOCK`) — R5;
- в зоне стола **любой** клик отменяется (`event.setCancelled(true)`), чтобы не
  сработало использование блока (например, открытие сундука);
- если в руке фишка (`ChipManager.isChipItem`) — дальше;
- **Шифт+ПКМ** → `ChipManager.cycle(player)` — сменить номинал;
- **ПКМ** → `table.tryPlaceBet(player, clickedBlock, interactionPoint)`.

### Шаг 2. Определяется, что это за ставка
`RouletteTable.tryPlaceBet`:

1. Проверка фазы: ставки принимаются только в `Phase.BETTING`, иначе «Ставки закрыты».
2. Проверка права `roulette.play`.
3. Клик пересчитывается в **локальные координаты стола** (lx, lz) и дробную
   позицию внутри блока (u, v) — через базис стола (forward/right по yaw).
4. `BetDetector.guess(...)` по координатам возвращает `Bet` (тип + набор чисел):
   - центр ячейки → STRAIGHT (число);
   - грань → SPLIT; угол → CORNER; край → STREET/SIXLINE;
   - за сеткой → DOZEN/COLUMN; дальше → RED/BLACK/EVEN/ODD/LOW/HIGH;
   - клик вне игровой зоны → `null` (ничего не происходит).

### Шаг 3. Проверка лимитов — `BetBook.validate`
`bets/BetBook.java`. Лимиты читаются из `config.yml`:

| Лимит | Что считает |
|---|---|
| `min-bet: 10` | ставка не меньше минимума |
| `max-bet: 100000` | **суммарно по всем игрокам** на этой позиции; порог `positionTotal >= max-bet` |
| `max-total-per-spin: 500000` | суммарно **только этого игрока** за прокрутку |

Возвращает статус `OK / BELOW_MIN / ABOVE_MAX / ABOVE_TOTAL`; в последних трёх
случаях игрок получает сообщение и ставка не принимается.

### Шаг 4. Списание денег — `BalanceProvider.withdraw`
`balance/FcpBalanceProvider.java`:

```java
if (getBalance(player) < amount) return false;   // check
economy.removeCoins(player, (int) amount);       // then remove
```

Это принципиально: в FCP `removeCoins` **не проверяет достаточность** и молча
сносит баланс в `0`. Поэтому обязателен check-then-remove.

### Шаг 5. Запись ставки — `BetBook.put` (R1)
Ключ реестра: **`UUID игрока + ":" + Bet.key()`**, где `Bet.key()` —
`ТИП:числа` (позиция без игрока).

- новый ключ → ставка добавляется;
- тот же игрок + та же позиция → `existing.addAmount(...)` (склейка);
- **другой** игрок на той же позиции → другой ключ → отдельная ставка.

### Шаг 6. Фишка в мире
`spawnChipStand` — невидимый ArmorStand с блоком-ковром на голове (цвет по
`chips.colors`), стопкой по высоте.

### Шаг 7. Спин и выплата
`RouletteTable.beginSpin()` (по таймеру или `/roulette spin`, R2):

- `winningNumber = ThreadLocalRandom.current().nextInt(37)` — честно 0..36;
- фаза `SPINNING`, анимация ~12 сек (`animateSpin`, easing);
- `finishSpin()`:
  - для каждой ставки из `bets.all()` (а не «по позиции»): если число ставки
    содержит выигрышное → `Payout.compute(bet, returnStake)` →
    `deposit(bet.getPlayer(), win)` **именно этому игроку**;
  - broadcast выигрыша (радиус `broadcast-radius`), личное сообщение;
  - фаза `RESULT`, потом `startBettingPhase()` → `bets.clear()`.

Выплаты (`BetType`): STRAIGHT 35, SPLIT 17, STREET 11, CORNER 8, SIXLINE 5,
DOZEN/COLUMN 2, равные шансы 1. При `return-stake-on-win: true` выигрыш =
`amount * payout + amount`.

---

## 4. Что было сломано и как починено (R1–R11)

### R1 — изоляция ставок (критический)
**Было:** ставки лежали в `LinkedHashMap<String, Bet>` с ключом `Bet.key()`
(только позиция). Два игрока на «красное» перезаписывали друг друга, выигрывал
один, а второй терял деньги.

**Стало:** класс `BetBook`, ключ `UUID:positionKey`.
- `validate()` считает `max-bet` по **всем** ставкам на позиции, `max-total` —
  по **одному** игроку.
- `put()` склеивает только совпадение полного ключа.
- Выплаты идут по `bet.getPlayer()`.

### R2 — `/roulette spin` под админкой
**Было:** `case "spin" -> spin(sender)` — крутил любой.
**Стало:** `case "spin" -> requireAdmin(sender, () -> spin(sender));`.

### R3 — выгрузка чанка
**Проблема:** сущности стола неперсистентны (`setPersistent(false)`). При
выгрузке чанка сервер их удаляет, тикер продолжает работать, ставки «уходят
в пустоту».

**Стало в `tick()`:**
- чанк центра не загружен → `return` (таймеры стоят);
- иначе `ensureEntitiesAlive()`: если `pockets.size() != 37`, шарик мёртв или
  любая лунка мертва → удалить остатки, `spawnEntities()`, один `log.info`;
- после респауна ровно 37 лунок, 1 шарик, голограммы — без дублей.

### R4 — взрывы
`TableListener`:
- `BlockExplodeEvent` (HIGH, ignoreCancelled) — `blockList().removeIf(...)`
  для блоков, где `findTableAt(loc) != null` → TNT не ломает стол;
- `EntityDamageByBlockEvent` по `BLOCK_EXPLOSION`/`ENTITY_EXPLOSION`/`WITHER`
  → `cancel` для `isCasinoEntity` → взрыв не бьет лунки/шарик/голограммы.

### R5 — только правая кнопка
**Было:** обработчик реагировал на любой клик → ЛКМ тоже ставил ставку.
**Стало:** ставка и Шифт+ПКМ только на `RIGHT_CLICK_AIR/BLOCK`; отмена
использования блока остаётся для любых кликов.

### R6 — «Игрок не найден»
`messages.player-not-found: "&cИгрок не найден."` + использование в `give()`
и `set()`. Раньше при несуществующем нике показывалось `table-not-found`.

### R7 — опечатка в `chips.colors`
`ChipManager.reload()`: парсинг ключа обёрнут в `try/catch NumberFormatException`,
ключ пропускается с `log.warning("chips.colors: ключ '<key>' не число — пропущен")`.
Раньше `/roulette reload` падал.

### R8 — монеты FCP
- `FcpBalanceProvider(EconomyService, FcpCurrency)` — обёртка над
  `Frontiks.Craft.api.EconomyService`: `getBalance`/`deposit`/`withdraw`
  (check-then-remove, long→int cap), `save()` — no-op.
- `FcpHook.create(plugin)` — единственное место, где плагин трогает FCP API:
  `FcpServices.find(EconomyService.class)`; если сервиса нет — `null`.
- `CasinoPlugin`: `FcpHook.create` в `try/catch (NoClassDefFoundError | IllegalStateException)`
  → при отсутствии FCP fallback на `FileBalanceProvider` (тестовый `balances.yml`).
- `plugin.yml`: `softdepend: [FCP-Core, FCP-EconomyMarket]`.
- `pom.xml`: `Frontiks:fcp-core:1.0.0` (provided).
- `config.yml`: `fcp-currency: coins | dcoins`.

### R9 — ребрендинг
Пакет `ru.casino.roulette` → `Frontiks.Craft.casino`; `name: fcp-CasinoRoulette`;
`main: Frontiks.Craft.casino.CasinoPlugin`; `author: Frontiks`;
`groupId Frontiks`, `artifactId fcp-casino-roulette`, версия 1.0.0;
джар `fcp-casino-roulette-1.0.0.jar`; лог «fcp-CasinoRoulette включён…».
Игровые сообщения и префикс «Казино | …» **не менялись**.

### R10 — тесты
JUnit 5 + maven-surefire-plugin. **32 теста**:
`WheelNumbersTest` (4), `BetTypeTest` (2), `BetTest` (4), `BetDetectorTest` (10 —
13 типов + клик вне стола → null), `BetsIsolationTest` (4), `PayoutTest` (2),
`FcpBalanceProviderTest` (6). В обычной среде: `mvn test`.

### R11 — документация
`README.md` (пользовательская) + `REPORT.md` (отчёт) + этот файл.

---

## 5. Валюта: три режима работы

| Ситуация | Что подключается | Поведение |
|---|---|---|
| Есть FCP-Core + FCP-EconomyMarket | `FcpBalanceProvider` | баланс = монеты FCP (`%fcp_coins%`), списания/выплаты реальные |
| FCP есть, сервис не зарегистрирован | `null` → fallback | warning, тестовый кошелёк `balances.yml` |
| FCP нет вообще | `FileBalanceProvider` | warning «FCP Economy не найден…», всё работает на файле |

Переключение валюты — `fcp-currency: coins` (основная) или `dcoins` (донат).

**Красная линия:** никаких Vault-обёрток и параллельных кошельков — монеты
только через `EconomyService` FCP. `FileBalanceProvider` остаётся только
как fallback для стендов без FCP.

---

## 6. Как собрать и запустить

### В обычной среде (есть Maven + JDK 21)

```bash
# 1. положить FCP-Core в локальный Maven (provided-зависимость)
mvn install:install-file -Dfile=fcp-core-1.0.0.jar \
    -DgroupId=Frontiks -DartifactId=fcp-core -Dversion=1.0.0 -Dpackaging=jar

# 2. собрать
mvn -B clean package

# 3. тесты
mvn test
```

Готовый джар: `target/fcp-casino-roulette-1.0.0.jar`.

### В песочнице этой сессии
Maven и Maven Central недоступны, поэтому сборка сделана напрямую JDK 21:
`javac --release 21` + `jar`. Результат — `fcp-casino-roulette-1.0.0.jar`
(51 КБ, 25 классов, `plugin.yml` и `config.yml` внутри), sha256
`c4de425b73d58c50d29fd6d8e289e9234819b47f148cc92eaab70b429e30191e`.
Тесты прогнаны самописным раннером поверх стабов FCP/JUnit: **32/32 зелёные**.
Это ограничение песочницы, а не проекта — в бою `mvn -B clean package` соберёт то же.

### Запуск на сервере
1. `fcp-casino-roulette-1.0.0.jar` → `plugins/`, перезапуск.
2. Убедиться, что стоят FCP-Core и FCP-EconomyMarket (soft-зависимости).
3. `/roulette balance` — должно совпадать с `%fcp_coins%`.
4. `/roulette create` — построит стол; взять фишку (подсолнух с именем «ФИШКА»).
5. ПКМ по ячейке — ставка; Шифт+ПКМ — сменить номинал.

---

## 7. Приёмочные сценарии ТЗ (как проверить вручную)

| # | Сценарий | Ожидание |
|---|---|---|
| 1 | Два игрока ставят по 100 на красное | Каждому выплата 200 (свои ставки, не одна на двоих) |
| 2 | A ставит 99990, B ставит 10 на ту же позицию | B получает `above-max` (порог `positionTotal >= max-bet`) |
| 3 | ЛКМ по столу | Ставка **не** ставится |
| 4 | TNT под столом | Блоки стола не ломаются, сущности целы |
| 5 | Выгрузить чанк и вернуться | Стол пересоздаётся, 37 лунок, 1 шарик, игра продолжается |
| 6 | `/roulette spin` без прав | «Недостаточно прав» |
| 7 | `/roulette give ghost 100` | «Игрок не найден.» |
| 8 | Опечатка в `chips.colors` + `/roulette reload` | Warning в лог, reload не падает |
| 9 | С FCP: баланс = `%fcp_coins%`, ставка 100 | −100; выигрыш +200; баланс 50 при ставке 100 остаётся 50 |
| 10 | Без FCP | Warning в лог, казино работает на `balances.yml` |

---

## 8. Ограничения и важные нюансы

- **Только Paper API 1.21.x, без NMS.** JDK 21.
- `max-bet` — **суммарный** лимит позиции по всем игрокам; порог считается
  достигнутым при `positionTotal >= max-bet` (т.е. ровно 100000 уже нельзя).
- `removeCoins` в FCP не проверяет достаточность — поэтому в провайдере
  обязательно check-then-remove (не «чинить» на это место).
- FCP-EconomyMarket/FCP-Core **не входят** в поставку — ставятся отдельно.
- `TableBuilder` (блоки стола), типы ставок и выплаты **не менялись**.
- При деплое сверьте реальные сигнатуры `EconomyService`/`FcpServices` с вашим
  FCP-Core: в песочнице использовался минимальный стаб API.
