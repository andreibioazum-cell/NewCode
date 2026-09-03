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
чистом ISO C11 — без Kotlin, VM и GC; формат `code.xml` сохранён. Подробнее:
[`c_runtime/README.md`](c_runtime/README.md).

## Движок: сцена + код вместо блоков ##

Редактор проекта переделан в **полноценный движок** в том же стиле:

- **Сцена** — при открытии проекта сразу видна сцена как в игровом движке:
  фон и все спрайты, нарисованные в реальном размере и повороте.
  Спрайт можно **перетащить мышцем/пальцем** (позиция обновляется на месте)
  и **тапнуть** — откроется его редактор. Кнопка `+` добавляет спрайт
  (галерея/медиа-библиотека Catrobat, галерея устройства, камера, пустой),
  кнопка `▶` запускает игру. В меню — «Сменить сцену» (мульти-сцены)
  и «Список спрайтов» (старый вид).
- **Код вместо блоков** — палитра категорий блоков убрана: у каждого
  спрайта закладка **Code** со списком скриптов (`when started`,
  `when touched`, `when <msg> received`, `when cloned`). Редактор кода —
  полноэкранный, моноширинный, с подсветкой синтаксиса; код компилируется
  в bricks движка при сохранении (см. язык ниже). Старые проекты
  отображаются тем же редактором: bricks превращаются в текст.
- **Спрайт = картинка + скрипты + звук** — закладки `Code` / `Sprite`
  (галерея видов, добавление из медиа-библиотеки/файлов/камеры/рисовалки) /
  `Sounds` (записать, добавить из файлов или библиотеки).

### Язык скриптов

```java
when started {
    set_variable(score, 0);
    say("Tap me!");
}
when touched {
    change_variable(score, 1);
    if (score >= 10) {
        say("You win!");
        stop_all_sounds();
        exit_stage();
    } else {
        broadcast("score");
        play_sound("ding");
    }
}
when score received {
    repeat (3) {
        move(10);
        turn(15);
        wait(0.2);
    }
    while (size < 100) {
        change_size(5);
    }
    forever {
        next_look();
        wait(0.5);
    }
}
```

Команды: `move, turn, turn_left, point, change_x, change_y, set_x, set_y,
place, glide, set_size, change_size, say, think, hide, show, hide_text,
next_look, clone, delete, come_to_front, bounce_on_edge, wait, stop,
exit_stage, broadcast, wait_broadcast, play_sound, stop_all_sounds,
play_note, set_variable, change_variable, read`.
Управление: `if/else`, `repeat (n)`, `while (cond)`, `do/while`, `forever`.
Выражения: числа, строки, переменные, `+ - * / % **`, `== != < > <= >=`,
`and/or/not`, `abs, sqrt, round, floor, ceil, sin, cos, tan, atan2,
min, max, pow, random(a, b)`.
Справка со списком команд — в меню редактора кода («Commands»).

Хранение не изменилось: код транслируется в bricks и сохраняется в том же
`code.xml` — проекты остаются совместимыми с оригинальным Catroid и с
C-компилятором `c_runtime`.

## Быстрая сборка ##

- `org.gradle.caching=true` — кэш задач между запусками (повторные
  сборки существенно быстрее);
- Kotlin компилируется **внутри Gradle-демона**
  (`kotlin.compiler.execution.strategy=in-process`) — без второго JVM;
- тяжёлые статанализаторы (PMD/checkstyle/JaCoCo) больше не настраиваются
  на каждый сбор — только по явному флагу:
  `./gradlew check -PqualityChecks`.

Быстрая сборка APK:

```bash
./gradlew assembleCatroidDebug -x lint -x test
```

Повторная сборка после небольшой правки на тёплом демоне укладывается
примерно в минуту; холодная сборка (первые скачивания зависимостей)
естественно дольше.

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
