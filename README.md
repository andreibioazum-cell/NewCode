# NewCode #

**NewCode** — форк Catroid, в котором исполнение визуальных проектов
перенесено с Java/Kotlin на **чистый C с компиляцией в машинный код**.

Проект Catrobat (`code.xml`) **компилируется**: сначала в C-исходник,
затем обычным C-компилятором — в нативный исполняемый файл. Никакого
байт-кода, никакой виртуальной машины, никакого сборщика мусора:
значения живут на стеке, память C-блоков управляется явно.

```bash
cd c_runtime && make && ./newcode run examples/c_memory.xml
```

Подробности — в [`c_runtime/README.md`](c_runtime/README.md).

Из кодовой базы Android-приложения удалены все блоки-расширения
(**Lego NXT/EV3, Raspberry Pi, Arduino, Phiro, Parrot Drone,
JumpingSumo, NFC, Chromecast, Embroidery/Stitch, Gamepad**), а также
сетевые блоки **«Веб-запрос»** (`WebRequestBrick`) и
**«Получить изображение из»** (`LookRequestBrick`/`BackgroundRequestBrick`) —
поддерживаются только основные категории: события, управление,
движение, внешний вид, звук, переменные/списки, формулы и низкоуровневые
C-блоки (теперь включая почти весь базовый C: `while`, `do-while`, `for`,
`switch/case`, `goto/label`, `?:`, `++/--`, `sizeof`, `struct`, `enum`,
`assert`, побитовые операции `& | ^ << >> ~`). Приложение носит имя
**NewCode** (Java-пакеты `org.catrobat.catroid` оставлены без изменений
ради совместимости формата проектов).

Интерфейс Android переведён в **горизонтальный режим**, а нативное ядро
получило 3D-позу (`x/y/z`, pitch/yaw/roll), перспективную камеру 16:9 и
визуальные блоки управления глубиной и вращением. Само 3D-ядро написано на
чистом ISO C11 — без Kotlin, VM и GC; существующий редактор блоков и формат
`code.xml` сохранены. Подробнее: [`c_runtime/README.md`](c_runtime/README.md).

Проекты `.catrobat` сохраняются/экспортируются в **ультра-сжатом ZIP**
(`Deflater.BEST_COMPRESSION` — раньше архив записывался вообще без сжатия),
при этом остаются обычным ZIP, совместимым с оригинальным Catroid.

Дополнительно вычищено для лёгкости сборки: локали сокращены до
**английской и русской** (~12 МБ переводов 70+ языков удалены),
instrumented-тесты UI с их `.catrobat`-проектами (~29 МБ) и
соответствующие CI-workflows удалены — JVM unit-тесты
(`catroid/src/test`) и сборка APK (`main.yml`) сохранены.

---

## Оригинальное описание Catroid ##

**Catroid** is a visual coding IDE and interpreter for Android for the Catrobat programming language.

**Catrobat** is a visual programming language and a set of creativity tools for smartphones.
Catrobat projects can be created using Catrobat's Android apps available on [Google Play](https://play.google.com/store/apps/developer?id=Catrobat) and iPhone apps available on [Apple's app store](https://apps.apple.com/us/developer/international-catrobat-association-verein-zur-foerderung/id1117935891).

For more information oriented towards developers, check out our [developers page](https://developer.catrobat.org/).

# Issues #

For reporting issues or improving this project, use the issue tracker of this repository.
