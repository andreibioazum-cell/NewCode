# NewCode — компилятор проектов Catrobat на чистом C

**NewCode** компилирует визуальные проекты **Catrobat** (`code.xml`) в
**исходник на C**, который обычным C-компилятором (`cc`) собирается в
**нативный машинный код** (ELF-исполняемый файл) и запускается.

Никакого байт-кода, никакой виртуальной машины и **никакой сборки
мусора (GC)**:

* весь код проекта транслируется в настоящий C — `Repeat` → `for`,
  `Forever` → `for(;;)`, `If` → `if`, формулы → выражения;
* `break` / `continue` / `return` — это машинные C-операторы;
* значения (`NcVal`) и списки (`NcList`) живут **на стеке и в статике** —
  для них куча не используется вовсе;
* C-блоки (`malloc`/`calloc`/`realloc`/`free`/`memcpy`/`memset`,
  разыменование указателей) работают с **реальной памятью процесса**
  через явные `malloc`/`free` — освобождение только явное.

Интерпретатор (`newcode interp`) сохранён как отладочный режим; основной
режим — компиляция.

## Сборка и использование

```bash
cd c_runtime
make                 # -> ./newcode (компилятор)

./newcode run    examples/c_memory.xml   # скомпилировать в машинный код и выполнить
./newcode build  project/code.xml -o prog # скомпилировать в исполняемый файл prog
./newcode emit   project/code.xml -o p.c  # посмотреть сгенерированный C-код
./newcode interp project/code.xml        # (fallback) исполнить интерпретатором

make test            # юнит-тесты + сквозные тесты компилятора (XML->C->cc->запуск)
make run-example     # пример проекта C-блоков, скомпилированный в машинный код
```

Ключ `-k`/`--keep` оставляет сгенерированные исходники (путь печатается).

## Конвейер

```
code.xml ──cat_loader──► CatProject ──cat_compiler──► nc_program.c
                                                        │
                                          cc -std=c11 -O2 + nc_rt.h
                                                        ▼
                                            нативный исполняемый файл
                                          (машинный код, без VM и GC)
```

* `include/nc_rt.h` — мини-рантайм сгенерированных программ:
  `NcVal` (значение на стеке), арифметика/сравнения по правилам
  Catrobat, функции, списки без кучи, ожидания, типизированные
  обращения к памяти. Всё это — `static inline`-функции, которые
  компилятор разворачивает в машинный код вместе с программой проекта.
* `src/nc_rt_data.h` — встроенная копия `nc_rt.h` (компилятор записывает
  её рядом со сгенерированным кодом). Регенерируется из
  `include/nc_rt.h`, не редактируется вручную.

## Поддерживаемые блоки

| Категория      | Блоки                                                                 |
|----------------|------------------------------------------------------------------------|
| События        | When started, When tapped, When broadcast, Broadcast, Broadcast&Wait  |
| Управление     | Wait, Forever, Repeat, Repeat Until, If/Else, If Then, Stop, Note, Return, Break, Continue |
| Движение       | PlaceAt, SetX/Y, ChangeX/Y, Move N steps, Turn left/right, Point in dir, Glide |
| Внешний вид    | Show, Hide, SetSize, ChangeSize, Say, Think, Set/Next/Previous look   |
| Звук           | PlaySound, StopAllSounds, SetVolume, ChangeVolume                     |
| Данные         | SetVariable, ChangeVariable, AddToList, DeleteFromList, ClearList, InsertIntoList, ReplaceInList |
| Формулы        | +, −, ×, ÷, %, ^, сравнения, AND/OR/NOT, sin/cos/tan/sqrt/abs/round/floor/ceil/ln/log/exp/min/max/random/length/join/letter, USER_VARIABLE, USER_LIST, SENSOR |
| C-блоки        | Malloc, Calloc, Realloc, Free, Memcpy, Memset, Typedef, Cast, PointerSet, PointerGet — настоящие операции с памятью |
| Код (inline)   | ExecuteCCode (сырой C встраивается в вывод и исполняется нативно), ExecuteJavaCode (хранит исходник Java; исполняется скриптовым движком в Android-интерпретаторе) |
| Клоны          | Clone, DeleteThisClone, WhenCloned — настоящие zero-copy клоны (см. ниже) |
| Отладка/CLI    | PrintBrick (наше расширение — печатает значение в stdout)             |

**Расширения** (Lego NXT/EV3, Raspberry Pi, Arduino, Phiro, Parrot Drone,
JumpingSumo, NFC, Chromecast, Embroidery/Stitch, Gamepad) не
поддерживаются и при загрузке перечисляются в предупреждении.

### Семантика компиляции

* Скрипты `When started` вызываются из `main()` по порядку объявления;
  `When broadcast` — напрямую из диспетчера сообщения (компилятор
  статически связывает `Broadcast` с получателями). Скрипты выполняются
  последовательно — это детерминированное упрощение вместо параллельных
  fiber'ов интерпретатора.
* `Forever` после каждой итерации делает `nc_tick()` (пауза 16 мс), чтобы
  не занимать ядро busy-loop'ом.
* `Stop all scripts` → `exit(0)`, `Stop this script`/`Return` → `return`,
  `Break`/`Continue` вне цикла безопасно игнорируются.
* `typedef` разрешаются на этапе компиляции: `PointerSet`/`PointerGet`
  получают конкретный C-тип (`double*`, `int*`, `char*`, …) уже в
  сгенерированном коде.
* Указатели — настоящие адреса процесса, приведённые к `double` для
  хранения в переменных Catrobat. Небрежная работа с ними в C-блоках —
  честная небрежность C (например, запись по нулевому указателю
  завершит программу), поэтому экспериментируйте аккуратно.

### Клоны спрайтов

Модель «zero-copy» в обоих режимах: **скрипты, переменные и списки —
общие** с прототипом, отдельной является только сценическая поза
(x, y, курс, размер, прозрачность, яркость, видимость).

* **Интерпретатор** (`interp`): клон/прототип — это легкий `SpriteInst`.
  Создание клона копирует ~7 `double` (O(1), ноль strdup); удалённые
  клоны возвращаются в пул структур и переиспользуются — churn
  «создать/удалить» не гуляет по malloc/free. Потолок `NC_MAX_CLONES`
  (1024) защищает от лавины клонов: сверх лимита клон тихо не
  создаётся, проект не лагает. `Broadcast` получают и клоны.
* **Компилятор** (`run`/`build`): для спрайта со скриптами WhenCloned
  генерируется `clone_fire_<i>()`: фиксированный пул поз
  `NC_CLONE_CAP` (по умолчанию 16, переопределяется `-DNC_CLONE_CAP=`)
  + побитовая копия структуры позы. Клон синхронно выполняет скрипты
  WhenCloned и освобождает слот пула (статический рантайм
  последовательный). Пул заполнен => новый клон пропускается.

## Устройство

```
include/
  cat_mem.h         — обёртки malloc/realloc/free с учётом памяти (без GC)
  cat_value.h       — CatValue: number/string/bool/null (tagged union)
  cat_project.h     — Project → Scene → Sprite → Script → Brick + Formula
  cat_xml.h         — минималистичный XML-парсер (subset)
  cat_loader.h      — загрузка Catrobat code.xml в CatProject
  cat_compiler.h    — КОМПИЛЯТОР: CatProject -> C-исходник
  nc_rt.h           — мини-рантайм для сгенерированных программ
  cat_interpreter.h — интерпретатор (отладочный fallback-режим)
src/
  main.c            — CLI `newcode` (emit / build / run / interp)
tests/
  test_all.c        — юнит-тесты загрузчика/формул/интерпретатора
  test_c_blocks.c   — C-блоки в режиме интерпретатора
  test_clones.c     — zero-copy клоны интерпретатора (поза, пул, лимит)
  test_compiler.c   — сквозные тесты: XML -> C -> cc -> машинный код -> вывод
examples/
  hello.xml         — циклы, формулы, broadcast
  c_memory.xml      — C-блоки: указатели, memcpy/realloc, break/return
  clones.xml        — клоны: Clone/WhenCloned/DeleteThisClone
```

## Отличие от интерпретатора

`newcode interp` исполняет брикки по одному в fiber-планировщике — это
медленно и требует рантайма. `newcode run` один раз транслирует весь
проект в машинный код: дальше работает компилятор C и процессор, без
каких-либо структур интерпретации в рантайме. C-блоки при этом
перестают быть симуляцией — это настоящие `malloc`/`memcpy`/указатели.

## Что удалено из Java/Kotlin-стороны Catroid

В корневом проекте (`catroid/…`) удалены исходники расширений
(`nfc/`, `cast/`, `embroidery/`, `bluetooth/`, `devices/`, `src/mindstorms/`,
`src/phiro/`, `src/embroideryDesigner/` и соответствующие блоки), а также
мусор конфигурации (`.idea/`, `.run/`, дублирующий корневой `main.yml`,
интеграция Crowdin). Android-приложение носит имя **NewCode**
(Java-пакеты `org.catrobat.catroid` сохранены ради совместимости
формата проектов — это внутренние идентификаторы, а не брендинг).
