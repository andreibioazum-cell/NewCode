# catrobat_run — исполнение проектов Catrobat на чистом C

Самостоятельный интерпретатор проектов **Catrobat** (`code.xml`),
написанный на C11, без Java/Kotlin/Android. Собирается любым C-компилятором
(gcc/clang), зависит только от libm.

Что поддерживается — **только базовые блоки** Catrobat:

| Категория      | Блоки                                                                 |
|----------------|------------------------------------------------------------------------|
| События        | When started, When tapped, When broadcast, Broadcast, Broadcast&Wait  |
| Управление     | Wait, Forever, Repeat, Repeat Until, If/Else, If Then, Stop, Note     |
| Движение       | PlaceAt, SetX/Y, ChangeX/Y, Move N steps, Turn left/right, Point in dir, Glide |
| Внешний вид    | Show, Hide, SetSize, ChangeSize, Say, Think, Set/Next/Previous look   |
| Звук           | PlaySound, StopAllSounds, SetVolume, ChangeVolume                     |
| Данные         | SetVariable, ChangeVariable, AddToList, DeleteFromList, ClearList, InsertIntoList, ReplaceInList |
| Формулы        | +, −, ×, ÷, %, ^, сравнения, AND/OR/NOT, sin/cos/tan/sqrt/abs/round/floor/ceil/ln/log/exp/min/max/random/length/join/letter, USER_VARIABLE, USER_LIST, SENSOR |
| Отладка/CLI    | PrintBrick (наше расширение — печатает значение в stdout)             |

**Расширения удалены и/или молча игнорируются** при загрузке XML:
Lego NXT/EV3, Raspberry Pi, Arduino, Phiro, Parrot Drone, JumpingSumo,
NFC, Chromecast, Embroidery/Stitch, Gamepad.

Получить перечень пропущенных блоков в конкретном проекте можно из вывода
`catrobat_run` — после загрузки печатается список неизвестных типов.

## Сборка и запуск

```bash
cd c_runtime
make           # -> ./catrobat_run
make test      # прогон юнит-тестов
make run-example
```

Запуск проекта:

```bash
./catrobat_run path/to/code.xml
./catrobat_run path/to/code.xml --ticks 5000 --dt 0.016 --mem-limit 67108864
```

## Устройство

```
include/
  cat_mem.h         — обёртки над malloc/realloc/free, учёт памяти, арена
  cat_value.h       — CatValue: number/string/bool/null (tagged union)
  cat_project.h     — Project → Scene → Sprite → Script → Brick + Formula
  cat_xml.h         — минималистичный XML-парсер (subset)
  cat_loader.h      — загрузка Catrobat code.xml в CatProject
  cat_interpreter.h — кооперативный планировщик fiber'ов, eval формул
src/
  main.c            — CLI-обёртка
tests/
  test_all.c        — юнит-тесты
examples/
  hello.xml         — маленький проект: repeat, JOIN, sensor, broadcast
```

### Управление памятью

Все аллокации проходят через `cat_malloc/cat_realloc/cat_free`,
которые:

* хранят размер блока в префиксе и ведут учёт `used/peak`,
* уважают `cat_mem_set_limit(bytes)` — при превышении печатают
  диагностическое сообщение и вызывают `abort()`,
* позволяют по завершении убедиться, что `allocs == frees`.

Для быстрых временных аллокаций доступна арена (`CatArena`):
bump-allocator с ростом блоков.

### Типы значений

`CatValue` — tagged union из number (double), bool, string (владеющий
`char*`) и null. Все конверсии (`to_number/to_bool/to_cstring`)
реализованы по правилам Scratch/Catrobat: строка «42» → число 42,
число 1.0 печатается как «1» (без «.0»), boolean «false»/«0»
трактуется как ложь и т.п. Строки в `CatValue` всегда владеющие —
не забудьте `cat_value_free`.

### Планировщик

Каждый запущенный скрипт — «fiber» со стеком фреймов. Один фрейм
представляет плоский список брикков (тело `Forever`/`Repeat`/`If`).
Планировщик round-robin: `Wait` и `BroadcastWait` ставят fiber на
паузу; `Broadcast` разбудит их. `Forever` даёт yield в конце каждой
итерации, чтобы не блокировать поток.

## Что удалено из Java/Kotlin-стороны Catroid

В корневом проекте (`catroid/…`) удалены исходники расширений,
чтобы соответствующие блоки исчезли из кодовой базы:

* Директории: `nfc/`, `cast/`, `embroidery/`, `bluetooth/`, `devices/`.
* Source-sets: `src/mindstorms/`, `src/phiro/`, `src/embroideryDesigner/`.
* Файлы `content/bricks/*` и `content/actions/*`, чьи имена
  начинаются с `Arduino`, `Drone`, `JumpingSumo`, `Lego`, `Phiro`,
  `Raspi`, `Stitch`, `RunningStitch`, `TripleStitch`, `ZigZagStitch`,
  `StopRunningStitch`, `WriteEmbroideryToFile`, `WhenNfc`, `SetNfcTag`,
  `WhenRaspiPinChanged`, `WhenGamepadButton`, `Stamp*` и т.п.

⚠️ Полная перекомпиляция Android-приложения после этого требует также
почистить ссылки в фабриках блоков (`CategoryBricksFactory`,
`CategoryBeginnerBricksFactory`), в XStream-маппингах загрузчика проектов
(`XstreamSerializer` и т.п.), в ресурсах строк и layout'ах. Эту чистку
можно делать инкрементально: компилятор Kotlin/Java укажет на каждый
битый импорт. Само же **исполнение** проектов теперь целиком выполняется
через `c_runtime/` и от Java-стороны не зависит.
