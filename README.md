# fcp-CasinoRoulette

Европейская рулетка для **Paper 1.21.x** — полноценный модуль семейства **FCP**.
Физический стол в мире: колесо из 37 лунок, шарик с кинематографичной прокруткой
(~12 сек), ставки кликом фишкой по ячейкам стола.

**Валюта:** монеты FCP (`Coins`, плейсхолдер `%fcp_coins%`) через
`EconomyService` из FCP-Core. Если FCP-EconomyMarket на сервере нет — плагин
автоматически работает на тестовом кошельке `balances.yml`.

## Сборка

Нужны **JDK 21** и **Maven**.

FCP-Core — provided-зависимость, её нужно положить в локальный Maven:

```bash
mvn install:install-file -Dfile=fcp-core-1.0.0.jar \
    -DgroupId=Frontiks -DartifactId=fcp-core -Dversion=1.0.0 -Dpackaging=jar
```

Затем:

```bash
mvn -B clean package
```

Готовый джар: `target/fcp-casino-roulette-1.0.0.jar` → положить в папку `plugins`.
`mvn test` запускает 32 юнит-теста (JUnit 5).

## Установка и запуск

1. Положить `fcp-casino-roulette-1.0.0.jar` в `plugins`, перезапустить сервер.
2. Убедиться, что стоят `FCP-Core` и `FCP-EconomyMarket` (обе зависимости
   **soft** — без них плагин стартует на тестовой валюте).
3. Проверить валюту: `/roulette balance` должно совпадать с `%fcp_coins%`.
4. Встать туда, где будет центр колеса, и смотреть в сторону игроков:
   `/roulette create` — плагин построит стол блоками.
5. Взять в руку фишку (подсолнух с именем `&6&lФИШКА`) — **ПКМ** по ячейкам
   ставит ставку, **Шифт+ПКМ** переключает номинал.

## Ставки (стандартный набор)

| Тип | payout | | Тип | payout |
|---|---|---|---|---|
| Число (STRAIGHT) | 35 | | Дюжина / Колонка | 2 |
| Сплит (2 числа) | 17 | | Красное / Чёрное | 1 |
| Стрит (3 числа) | 11 | | Чёт / Нечёт | 1 |
| Каре (4 числа) | 8 | | 1-18 / 19-36 | 1 |
| Шесть номеров | 5 | | | |

Клик по центру ячейки — число; по грани — сплит; по углу — каре;
у края сетки — стрит/шесть номеров. За сеткой — дюжины, колонки и равные шансы.
Номинал фишки и цвет стопки настраиваются в `config.yml`.

## Команды

| Команда | Права | Описание |
|---|---|---|
| `/roulette create` | roulette.admin | создать стол |
| `/roulette remove [id]` | roulette.admin | удалить стол (блоки в мире остаются) |
| `/roulette list` | — | список столов |
| `/roulette chip [сумма]` | — | выбрать номинал фишки |
| `/roulette balance` | — | баланс (монеты FCP или тестовая валюта) |
| `/roulette give <игрок> <сумма>` | roulette.admin | выдать валюту |
| `/roulette set <игрок> <сумма>` | roulette.admin | установить баланс |
| `/roulette spin` | roulette.admin | форсировать прокрутку |
| `/roulette reload` | roulette.admin | перезагрузить конфиг |

## Валюта FCP

Всё идёт через `Frontiks.Craft.casino.balance.BalanceProvider`. В боевой сборке
подключён `FcpBalanceProvider` — обёртка над `EconomyService`:

- `withdraw` делает **check-then-remove**: `removeCoins` в FCP не проверяет
  достаточность и молча сносит баланс в `0`, поэтому сначала проверяется
  `getCoins`, только потом списывается.
- `fcp-currency: coins` (по умолчанию) — основная валюта;
  `fcp-currency: dcoins` — донат-монеты (`%fcp_donate_coins%`).
- Лимиты казино (`max-bet: 100000`, `max-total-per-spin: 500000`) укладываются
  в `int`; long → int обрезается.
- Без FCP-EconomyMarket — `FileBalanceProvider` (`balances.yml`).

## Защита стола

- Блоки стола защищены от ломания/установки (кроме `roulette.admin`).
- Взрывы (TNT, крипер, Wither) не ломают блоки стола и не бьют сущности казино.
- Сущности стола неперсистентны: если чанк выгрузился, при возврате стол
  пересоздаёт 37 лунок, шарик и голограммы (в логе «сущности пересозданы»).
- Ставки принимаются **только правой кнопкой**; ЛКМ ставку не ставит.

## Конфиг (`config.yml`)

Ключевые параметры:

```yaml
fcp-currency: coins        # coins | dcoins
min-bet: 10
max-bet: 100000            # суммарный лимит на позицию (по всем игрокам)
max-total-per-spin: 500000 # лимит ставок одного игрока за прокрутку
return-stake-on-win: true
broadcast-radius: 30
chips:
  denominations: [10, 50, 100, 500, 1000]
  colors:
    10: WHITE_CARPET
```

Сообщения — секция `messages` (`&`-коды, плейсхолдеры `{key}`).
Добавлено `messages.player-not-found: "&cИгрок не найден."`.

## Изменения v1.1

- **R1 (критично):** ставки изолированы по игрокам. Ключ реестра —
  `UUID:positionKey`; два игрока на одной позиции получают два независимых
  выигрыша, повтор того же игрока склеивается. `max-bet` считается суммарно
  по всем игрокам на позиции.
- **R2:** `/roulette spin` требует `roulette.admin`.
- **R3:** при выгрузке чанка стол пересоздаёт сущности и продолжает работу.
- **R4:** защита блоков и сущностей стола от взрывов.
- **R5:** ставка и смена номинала — только ПКМ.
- **R6:** `/roulette give|set <незнакомец>` → «Игрок не найден.».
- **R7:** опечатка в `chips.colors` не роняет `/roulette reload`.
- **R8:** подключены монеты FCP (`FcpBalanceProvider` + `fcp-currency`);
  fallback — тестовый кошелёк.
- **R9:** ребрендинг: пакет `Frontiks.Craft.casino`, имя `fcp-CasinoRoulette`,
  артефакт `fcp-casino-roulette-1.0.0.jar`.
- **R10:** 32 юнит-теста JUnit 5 (WheelNumbers, BetType, Bet, BetDetector,
  BetsIsolation, Payout, FcpBalanceProvider).
- **R11:** README и отчёт.

## Структура

```
src/main/java/Frontiks/Craft/casino/
├── CasinoPlugin.java          — точка входа, подключение валюты FCP/fallback
├── balance/                   — BalanceProvider, FcpBalanceProvider, FileBalanceProvider, FcpCurrency
├── bets/                      — BetType, Bet, BetBook, BetDetector, Payout
├── table/                     — RouletteTable (фазы, анимация), TableBuilder, TableManager
├── listeners/                 — клики фишкой, антигриф, взрывы
├── commands/                  — /roulette
└── util/                      — WheelNumbers, Messages, ChipManager
```

## Ограничения

- Только Paper API 1.21.x, без NMS.
- FCP-EconomyMarket/FCP-Core не входят в поставку — ставятся отдельно.
- `int`-лимиты FCP: ставки казино ограничены 500 000, переполнения нет.
- `max-bet` — суммарный лимит позиции по всем игрокам (порог считается
  достигнутым при `positionTotal >= max-bet`).
