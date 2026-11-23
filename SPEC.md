# fcp-CasinoRoulette — ТЗ: фикс-раунд + интеграция с FCP (v2)

**Кому:** кодер/нейросеть. **От кого:** владелец проекта.
**Цель:** (1) исправить 7 багов аудита (1 критический + 6 мелких); (2) подключить казино к **монетам FCP** (FCP Economy); (3) переименовать плагин в **fcp-CasinoRoulette** — полноценный модуль семейства FCP; (4) добавить юнит-тесты. **Не** редизайн, **не** новые фичи.

**Базовый исходник:** проект `CasinoRoulette` (Maven, 14 классов, ~1950 строк, package `ru.casino.roulette`). Неизменённый архив: `CasinoRoulette-source.zip`, sha256 `efb97c860b773f1bcc1e880427f228236fb2b81feff2dfdd908607e18612b975`. Если в твоих руках уже изменённое дерево — сверь каждый R-пункт с критериями приёмки: соответствует — не трогай, нет — доработай.

---

## 1. Красные линии (нарушение = отклонение)

1. **Игровую логику не переписывать.** Фазы (BETTING 20с → SPINNING ~12с → RESULT 5с), выплаты, геометрия стола, детектор ставок — сохраняются. Допустимое и **обязательное** исключение — механический ребрендинг (пакет/имя/артефакт) по **R9** и новый провайдер валюты по **R8**.
2. **Не менять типы ставок и выплаты** — таблица §2.2 инвариант европейской рулетки. 37 чисел, порядок `WheelNumbers.EUROPEAN_ORDER`, 18 красных.
3. **config.yml обратно совместим**: существующие ключи и смыслы не удаляются/не переименовываются; добавлять можно (обязательные новые — `messages.player-not-found`, `fcp-currency`).
4. **Монеты идут ТОЛЬКО через `EconomyService` FCP** (R8). Свой параллельный кошелёк,Vault-обёртки и прямое редактирование чужих конфигов — запрещено. Файл-кошелёк остаётся только как fallback на без-FCP стенд.
5. **Только paper-api 1.21.x, без NMS.** JDK 21, `mvn -B clean package`.
6. **Код FCP-EconomyMarket, FCP-Core, fcp-jobs и любых других плагинов не трогать.** У FCP-EconomyMarket есть плейсхолдер-команда `/casino` («Скоро будет!») — конфликта с `/roulette` нет, чужую команду не «чинить».
7. **Не деплоить на production** без явного одобрения владельца. Артфакт = исходник + JAR + отчёт.
8. **`TableBuilder` (блоки стола) не менять**: блоки персистентны в мире, сущности — нет; это осознанная архитектура.

---

## 2. Факты о проекте и FCP-экономике

### 2.1 Структура CasinoRoulette (кто за что отвечает)

```
src/main/java/ru/casino/roulette/
├── CasinoPlugin.java          — точка входа: конфиг, ChipManager, TableManager, BalanceProvider, listeners (строка подключения валюты: "this.balanceProvider = new FileBalanceProvider(this);")
├── balance/BalanceProvider.java     — интерфейс: long getBalance(UUID) / void deposit(UUID,long) / boolean withdraw(UUID,long) / void save()
├── balance/FileBalanceProvider.java — тестовый кошелёк: balances.yml, save на операцию
├── bets/BetType.java          — 13 типов ставок + payout
├── bets/Bet.java              — ставка: UUID+имя+тип+числа+сумма; key() = "TYPE:n1,n2" (БЕЗ игрока — корень R1)
├── bets/BetDetector.java      — тип ставки по локальным координатам клика
├── table/RouletteTable.java   — СЕРДЦЕ: фазы, tryPlaceBet, animateSpin, finishSpin (выплаты), сущности (37 лунок-armorStand, шарик, голограммы, стопки)
├── table/TableBuilder.java / table/TableManager.java — блоки стола / реестр (tables.yml)
├── listeners/TableListener.java — клики, антигриф
├── commands/RouletteCommand.java — /roulette <create|remove|list|chip|balance|give|set|spin|reload>
└── util/ ChipManager | WheelNumbers | Messages
```

### 2.2 Выплаты (ИНВАРИАНТ)

| Тип | payout | | Тип | payout |
|---|---|---|---|---|
| STRAIGHT | 35 | | DOZEN / COLUMN | 2 |
| SPLIT | 17 | | RED / BLACK / EVEN / ODD / LOW / HIGH | 1 |
| STREET | 11 | | | |
| CORNER | 8 | | | |
| SIXLINE | 5 | | | |

`finishSpin`: `win = amount*payout (+amount, если return-stake-on-win)`, депозит **получателю ставки**, анонс радиус 30.

### 2.3 FCP Economy — реальный API (из production-исходника FCP, сверять с ним)

Сервис **`Frontiks.Craft.api.EconomyService`** (API в **FCP-Core**, реализация регистрируется плагином **FCP-EconomyMarket** через `Bukkit.getServicesManager().register` в onEnable). Доступ — сервис-локатор **`Frontiks.Craft.api.FcpServices`**:

```java
static <T> T find(Class<T> type)    // null, если сервис не зарегистрирован
static <T> T require(Class<T> type) // null -> IllegalStateException
```

Методы `EconomyService` (все аргументы денег — **`int`, не long!**):

| Метод | Смысл |
|---|---|
| `int getCoins(UUID)` | баланс **основной валюты (монеты)** — плейсхолдер `%fcp_coins%` |
| `void addCoins(UUID, int)` | пополнить; **no-op при amount<=0**; cap на `Integer.MAX_VALUE` |
| `void removeCoins(UUID, int)` | списать; **no-op при amount<=0**; **НЕ проверяет достаточность** — `Math.max(0, balance-amount)`, т.е. при нехватке **молча сносит баланс в 0** |
| `getDCoins/addDCoins/removeDCoins` | вторая валюта — **донат-монеты** (`%fcp_donate_coins%`); для казино **не используется** (см. R8) |
| `Map<UUID,Integer> getCoinsMap()` | все балансы (для топков) |

Проверено по production-реализации `EconomyManager` (FCP-EconomyMarket) и по паттерну fcp-jobs: перед списанием ВСЕГДА проверяют `getCoins(uuid) >= amount` (см. `FishingGiveCommand`: `if (economy.getCoins(p) < price) { …недостаточно… } economy.removeCoins(p, price);`).

Балансы FCP лежат в `plugins/FCP-EconomyMarket/balances.yml` (UUID → coins/dcoins), сохраняются на каждую операцию.

### 2.4 Конфиг-лимиты казино

`min-bet: 10`, `max-bet: 100000` («максимум на одну позицию (суммарно)»), `max-total-per-spin: 500000`, `return-stake-on-win: true`. Все лимиты ≪ `Integer.MAX_VALUE` — int-арифметика FCP безопасна; **инвариант**: при изменении лимитов следить, чтобы они укладывались в int.

---

## 3. Требования

### R1 (КРИТИЧЕСКИЙ) — изоляция ставок по игрокам

**Проблема.** Ключ в `RouletteTable.bets` = `bet.key()` = `TYPE:числа` **без игрока**. Два игрока на одной позиции → вторая ставка сливается в `Bet` первого (`existing.addAmount`), выигрыш `finishSpin` платит **первому за обоих**. Деньги теряются.

**Фикс:**
1. Ключ карты = `UUID игрока + ":" + bet.key()`.
2. Повтор **того же** игрока на ту же позицию склеивается с его `Bet` (`addAmount`) — сохранить.
3. `max-bet` считается **суммарно по всем игрокам** на позиции (`chip + Σ ставок с этим positionKey` > max-bet → `above-max`).
4. `max-total-per-spin` без изменений (сумма ставок **этого** игрока).
5. Выплаты: каждый `Bet` → своему `bet.getPlayer()` (итерация `finishSpin` по `bets.values()` корректна — сохранить).
6. Javadoc `Bet`: зафиксировать, что полный ключ в таблице = `UUID:positionKey`.

**Приёмка:** два игрока по 100 на «красное» → красное → **каждому** 200, личка обоим; повторный клик того же игрока → один объект, сумма сложилась; A=99990 на «красное», B пытается 10 туда → B получает `above-max`, на другую позицию B может; сценарий покрыт тестом (R10).

### R2 — `/roulette spin` только админ

В `RouletteCommand.onCommand` все админ-подкоманды через `requireAdmin`, **кроме spin**. Фикс: `case "spin" -> requireAdmin(sender, () -> spin(sender));`
**Приёмка:** без `roulette.admin` → `no-permission`, прокрутка не стартует.

### R3 — разгруженный чанк: стол не работает «невидимым»

Сущности неперсистентны: чанк выгрузился → лунки/шарик/дисплеи мертвы, а тикер жив и ставки «в пустоту».
**Фикс (в `RouletteTable.tick()`):**
1. Сначала: чанк центра не загружен (`!center.getWorld().isChunkLoaded(x>>4, z>>4)`) → `return` (таймеры фаз стоят).
2. Чанк загружен, но `pockets.size() != 37 || ball == null || ball.isDead() ||` любая лунка `isDead()` → **респаун**: убрать мёртвых из списков (`pockets`, `ball`, `displays`; `chipStands.removeIf(isDead)`), очистить, `spawnEntities()`, один `log.info`.
3. После респауна ровно 37 лунок, 1 шарик, 1 title/status-дисплей (не задвоить).
4. Рестарт сервера уже работает через `TableManager.load()` → `start()` — не ломать.

**Приёмка:** ушли дальше выгрузки и вернулись → стол цел, работает, в логе «пересозданы», сущности не задвоены.

### R4 — защита стола от взрывов

**Фикс (в `TableListener`):**
1. `BlockExplodeEvent` (HIGH, ignoreCancelled): из `event.blockList()` удалить блоки, где `TableManager.findTableAt(block.getLocation()) != null`.
2. `EntityDamageByBlockEvent` по `BLOCK_EXPLOSION / ENTITY_EXPLOSION / WITHER`: `isCasinoEntity(entity)` → `setCancelled(true)` (страховка поверх `setInvulnerable`).

**Приёмка:** TNT под столом / крипер → блоки и 37 лунок + шарик целы, фаза не сбита.

### R5 — ставка только ПКМ

`onInteract` не фильтрует `Action` (ставка была и на ЛКМ), хотя README обещает ПКМ.
**Фикс:** отмена использования блока в зоне стола — для любых кликов (как сейчас); ставка и Шифт+ПКМ (номинал) — **только** `Action.RIGHT_CLICK_AIR`/`RIGHT_CLICK_BLOCK`; остальное — return. `Action` = `org.bukkit.event.block.Action`.
**Приёмка:** ЛКМ с фишкой — ставки нет; ПКМ — ставка; Шифт+ПКМ — номинал + actionBar.

### R6 — «Игрок не найден» в give/set

`/roulette give|set <незнакомец> N` сейчас → `table-not-found` (абсурд).
**Фикс:** в `config.yml` секция `messages`: `player-not-found: "&cИгрок не найден."`; в `RouletteCommand.give()` при `getPlayerExact == null` — это сообщение.
**Приёмка:** `/roulette give nobody123 100` → «Игрок не найден.»; на живом игроке поведение не изменилось.

### R7 — `chips.colors` не рвёт reload

`ChipManager.reload()`: `Long.parseLong(key)` на ключи `chips.colors` → опечатка в yml = `NumberFormatException` → `/roulette reload` падает.
**Фикс:** try/catch → пропустить ключ, `log.warning("chips.colors: ключ '<key>' не число — пропущен")`.
**Приёмка:** `colors: {10: WHITE_CARPET, oops: RED_CARPET}` → reload работает, warning в лог.

### R8 — подключение к монетам FCP (основная валюта — Coins)

**Какую монету берём:** основную **Coins** (`getCoins/addCoins/removeCoins`, плейсхолдер `%fcp_coins%`) — та же, которой платят/торгуют весь FCP-сервер. **DCoins** (донат-монеты) казино **не трогает**; переключение на них — только ключом конфига (владельцу может пригодиться):

```yaml
# config.yml
fcp-currency: coins   # coins (по умолчанию) | dcoins — какую валюту FCP списывает/зачисляет казино
```

**Фикс:**
1. Новый класс `FcpBalanceProvider implements BalanceProvider` (пакет `balance/`), обёртка над `EconomyService`:
   - конструктор: `(EconomyService economy, FcpCurrency currency)`; `FcpCurrency` — enum `COINS/DCOINS`, по нему выбирается тройка методов (getCoins/addCoins/removeCoins или getDCoins/addDCoins/removeDCoins).
   - `getBalance(uuid)` → `(long) econ.get*(uuid)`.
   - `deposit(uuid, amount)` → `econ.add*(uuid, (int) amount)` (add* сам игнорирует ≤0 и режет в int).
   - `withdraw(uuid, amount)` → **ЧЕК-THEN-REMOVE (обязательно)**: `if (amount <= 0) return true; if (econ.get*(uuid) < amount) return false; econ.remove*(uuid, (int) amount); return true;` — потому что `removeCoins` **не проверяет достаточность и сносит баланс в 0** (реальная семантика `EconomyManager`). Наивный `remove*` без проверки = слив чужих денег.
   - `save()` → no-op (FCP сам персистит свои балансы).
   - long→int: казино-лимиты ≤ 500 000, переполнения нет; если `amount > Integer.MAX_VALUE` — `withdraw` вернуть false, `deposit` отрезать (закомментировать инвариант).
2. **Wiring в `CasinoPlugin.onEnable`** (заменить строку `this.balanceProvider = new FileBalanceProvider(this);`):
   ```java
   EconomyService econ = FcpServices.find(EconomyService.class);
   if (econ != null) {
       FcpCurrency c = FcpCurrency.fromConfig(config.getString("fcp-currency", "coins"));
       this.balanceProvider = new FcpBalanceProvider(econ, c);
       getLogger().info("Валюта: монеты FCP (" + c + ")");
   } else {
       this.balanceProvider = new FileBalanceProvider(this);
       getLogger().warning("FCP Economy не найден — казино работает на тестовой валюте (balances.yml)");
   }
   ```
3. **plugin.yml**: `softdepend: [FCP-Core, FCP-EconomyMarket]` (FCP-Core даёт `FcpServices`/`EconomyService` на компиляции и рантайме, FCP-EconomyMarket — реализация; оба **soft** — казино должно запускаться и без них, на fallback).
4. **pom.xml**: добавить provided-зависимость на API:
   ```xml
   <dependency><groupId>Frontiks</groupId><artifactId>fcp-core</artifactId><version>1.0.0</version><scope>provided</scope></dependency>
   ```
   Артефакт должен лежать в локальном мавене: `mvn install:install-file -Dfile=fcp-core-1.0.0.jar -DgroupId=Frontiks -DartifactId=fcp-core -Dversion=1.0.0 -Dpackaging=jar` (jar — из FCP-релиза/прод-сервера; `fcp-economy-market` для компиляции **не нужен** — код видит только интерфейс из fcp-core).
5. Файл-кошелёк `FileBalanceProvider` — **сохранить** как fallback и для без-FCP стендов.

**Приёмка:**
- Сервер с FCP: `/roulette balance` == `%fcp_coins%`; ставка 100 → монеты сразу −100 (и в `plugins/FCP-EconomyMarket/balances.yml`); выигрыш 200 → +200; анонсы как раньше.
- Баланс 50, ставка 100 → отказ `not-enough`, баланс **остаётся 50** (НЕ 0!) — проверка чек-then-remove.
- Без FCP-EconomyMarket: плагин включается, в лог warning про тестовую валюту, ставки на balances.yml работают.
- `fcp-currency: dcoins` → казино бьёт по DCoins (getDCoins/addDCoins/removeDCoins).
- Покрыто тестом R10 с заглушкой `EconomyService`, реализующей **реальную** семантику `removeCoins` (clamp в 0).

### R9 — ребрендинг в FCP-семейство: fcp-CasinoRoulette

Механическое переименование (логика не меняется):
1. **Пакет**: `ru.casino.roulette` → `Frontiks.Craft.casino` (подпакеты сохранить: `balance`, `bets`, `table`, `listeners`, `commands`, `util`). Все package-декларации и импорты обновить.
2. **plugin.yml**:
   - `name: fcp-CasinoRoulette`
   - `main: Frontiks.Craft.casino.CasinoPlugin`
   - `author: Frontiks`
   - `description: FCP european roulette — physical table, bets on FCP coins`
   - `softdepend: [FCP-Core, FCP-EconomyMarket]` (из R8)
   - версия `1.0.0`, `api-version: '1.21'`, команды/права (`roulette.admin`, `roulette.play`) — **не менять**.
3. **Maven**: `groupId: Frontiks`, `artifactId: fcp-casino-roulette`, `version: 1.0.0` → джар **`fcp-casino-roulette-1.0.0.jar`** (по семейному стандарту fcp-core-1.0.0.jar, fcp-jobs-1.0.0.jar).
4. Лог-строка включения: `fcp-CasinoRoulette включён…`
5. Игровые сообщения/префиксы в `config.yml` **не менять** (текст «Казино | …» остаётся — это игровая вывеска, не имя плагина).

**Приёмка:** `mvn -B clean package` зелёный; `unzip -l fcp-casino-roulette-1.0.0.jar` — классы в `Frontiks/Craft/casino/…`; `plugin.yml` в джаре с новым name/main; джар стартует на Paper 1.21.x (вместе с FCP и без него).

### R10 — юнит-тесты (их сейчас **нет**)

`maven-surefire-plugin` + JUnit 5, тестов **≥ 18**:
1. `WheelNumbersTest` — 37 уникальных чисел 0..36; ровно 18 красных; `isRed(0)==false`.
2. `BetTypeTest` — таблица §2.2 побитово (13 констант).
3. `BetTest` — `key()` стабилен, без UUID; `describe()` по всем типам; `addAmount`.
4. `BetDetectorTest` — все 13 типов на «правильных» координатах клика + клик вне стола → null (зоны: сетка lz 8..11, зеро lx −1, дюжины lz 11, колонки lx 12, равные шансы lz 12).
5. `BetsIsolationTest` (R1) — два UUID на одной позиции → **два** `Bet`; повтор того же → склейка; лимит позиции суммарный.
6. `PayoutTest` — каждый `BetType` × `return-stake-on-win` true/false.
7. `FcpBalanceProviderTest` (R8) — заглушка `EconomyService` с реальной семантикой `removeCoins` (clamp 0): withdraw при нехватке → false и баланс не тронут; withdraw при достатке → true и −amount; deposit ≤0 → без изменений; COINS/DCOINS — выбор тройки методов; long→int cap.

**Приёмка:** `mvn -B clean package` — всё зелёное.

### R11 — README и отчёт

1. `README.md` переписать под fcp-CasinoRoulette: семейство FCP (softdepend), **валюта = монеты FCP (Coins)**, при отсутствии FCP — тестовый кошелёк; управление (ставка **только ПКМ**, Шифт+ПКМ — номинал); защита стола (взрывы, антигриф); команды (spin — админ); ключ `fcp-currency`; секция «Изменения v1.1».
2. Итоговый отчёт: список по R, `mvn test`, sha256 джара, ограничения (int-лимиты, fallback без FCP).

---

## 4. Out of scope (НЕ делать)

- Изменения в FCP-EconomyMarket/FCP-Core/fcp-jobs/любой другой FCP-модуль.
- Новые типы ставок, американская рулетка, мульти-столы, GUI, лидерборды, NMS.
- Деплой на production.

## 5. Порядок работы

1. **R1** + `BetsIsolationTest` → сборка зелёная.
2. **R2–R7** (мелкие фиксы: `RouletteTable`, `TableListener`, `RouletteCommand`, `ChipManager`, `config.yml`) → сборка.
3. **R9** (механика ребрендинга: пакет/имя/артефакт) → сборка зелёная **до** добавления FCP-кода (меньше правок импортов потом).
4. **R8** (FcpBalanceProvider + wiring + конфиг-ключ + pom) → сборка.
5. **R10** — полный тест-набор → `mvn -B clean package` зелёный.
6. **R11** — README + отчёт + джар + sha256 → сдача владельцу. **Не деплоить.**

## 6. Ручная приёмка в игре (после сборки)

| # | Сценарий | Ожидание |
|---|---|---|
| 1 | Два игрока по 100 на «красное» → красное | Каждому по 200, личка обоим |
| 2 | A 99990 + B 10 на «красное» (max-bet 100000) | B — `above-max`; на другую позицию B может |
| 3 | ЛКМ по ячейке с фишкой | Ставки нет |
| 4 | TNT под столом | Блоки и сущности целы |
| 5 | Уйти (выгрузка чанка) и вернуться | Стол цел, в логе «пересозданы» |
| 6 | `/roulette spin` без прав | `no-permission` |
| 7 | `/roulette give ghost 100` | «Игрок не найден.» |
| 8 | Опечатка в `chips.colors` + `/roulette reload` | Reload работает, warning |
| 9 | **С FCP:** `/roulette balance` vs `%fcp_coins%` | Равны |
| 10 | **С FCP:** ставка 100 → проверить монеты | −100 в `FCP-EconomyMarket/balances.yml` |
| 11 | **С FCP:** баланс 50, попытка ставки 100 | Отказ, баланс **50** (не 0) |
| 12 | **Без FCP:** старт плагина | Warning про тестовую валюту, всё работает |

## 7. Артфакт и релиз

- Исходник + **`fcp-casino-roulette-1.0.0.jar`** + отчёт. Версия 1.0.0, прод не тронут.
- Тест-кит владельцу: zip с джаром и инструкцией по §6.
- Production — только после явного слова владельца.

## 8. Примечания

- Сборка: JDK 21, Maven, `repo.papermc.io` (paper-api 1.21.4-R0.1-SNAPSHOT, provided) + fcp-core provided (по R8.4).
- Семантика `EconomyManager.removeCoins` (clamp в 0, без проверки) и паттерн чек-then-remove из fcp-jobs — сверить по production-исходнику FCP перед написанием `FcpBalanceProvider`.
- Владелец держит локальную **референсную** реализацию фиксов R1–R7 (без R8/R9) — можно сверяться при сомнениях; истина = критерии приёмки.
- Сообщения плагина — секция `messages` **config.yml** («&»-коды + `{key}` через `Messages.format`), отдельного messages.yml нет.
