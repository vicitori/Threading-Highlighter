# Threading Highlighter

Threading Highlighter — инструмент динамического анализа для разработчиков плагинов IntelliJ Platform.
Он показывает, какие [threading-контракты](https://plugins.jetbrains.com/docs/intellij/threading-model.html) фактически действуют на конкретных строках кода.

Инструмент состоит из двух компонентов:

1. **Агент (сбор данных).** Java-агент инструментирует threading-маркеры IntelliJ Platform (assertion-методы вида `assertIsDispatchThread`, `assertIsNonDispatchThread`, `assertSlowOperationsAreAllowed`). При каждом срабатывании маркера агент захватывает stack trace и записывает фреймы пользовательского кода в JSONL-файлы.
2. **Плагин (визуализация).** Плагин IntelliJ загружает записанные трассы и отображает gutter-иконки напротив строк, попавших в стек до маркера. Разработчик видит действующие threading-контракты прямо в редакторе.

![Gutter-иконки с threading-маркерами](gutter-view.png)

## Назначение

Threading-контракты IntelliJ (EDT-only, non-EDT, slow operations) не выражены в сигнатурах методов, поэтому их нарушение обычно обнаруживается только в рантайме (исключение) или на code review. Threading Highlighter переносит эту информацию в редактор: по gutter-иконке видно, через какой ассерт прошёл данный участок кода в реальном запуске. Это применимо для изучения threading-модели, раннего обнаружения тяжёлых операций на EDT и контроля контрактов при рефакторинге.

## Структура проекта

```
ThreadingHighlighter/
├── common/     — разделяемые модели данных (чистая Java, без Kotlin — для переиспользования агентом)
├── agent/      — Java-агент (Byte Buddy) для инструментации маркеров
├── plugin/     — плагин IntelliJ Platform для визуализации результатов
└── examples/   — демонстрационный плагин с тестовыми actions
```

## Модель сессии

По умолчанию каждый запуск JVM с агентом рассматривается как отдельная сессия: при старте агент удаляет trace-файлы предыдущего запуска. Как следствие, плагин отображает маркеры только для текущего состояния кода, и gutter-иконки соответствуют актуальным номерам строк даже после редактирования файлов между запусками. Дополнительная настройка для этого режима не требуется.

Режим дозаписи (накопление трасс за несколько запусков) включается системным свойством `threading.highlighter.append.session=true`. Свойство задаётся так же, как `threading.highlighter.project.dir`, — отдельной строкой `systemProperty` в блоке `runIde`:

```kotlin
tasks {
    runIde {
        jvmArgs("-javaagent:/path/to/agent.jar")
        systemProperty("threading.highlighter.project.dir", "${project.projectDir}")
        systemProperty("threading.highlighter.append.session", "true")
    }
}
```

Ограничение: дозапись корректна только при неизменном коде между запусками. После редактирования файла ранее записанные трассы ссылаются на устаревшие номера строк, из-за чего часть иконок отображается со смещением. При активной разработке используйте режим свежей сессии (не задавайте это свойство).

## Использование

### 1. Сборка агента и плагина

```bash
# Агент (shadow JAR со встроенными зависимостями)
./gradlew :agent:shadowJar

# Плагин (ZIP-дистрибутив; агент включается в него автоматически)
./gradlew :plugin:buildPlugin
```

Артефакты:

- Agent JAR — `agent/build/libs/agent.jar`
- Plugin ZIP — `plugin/build/distributions/plugin.zip`

### 2. Подключение агента

Агент подключается к JVM, код которой анализируется, — как правило, к тестовой sandbox-IDE, запускаемой задачей `runIde`. В `build.gradle.kts` анализируемого проекта задаются два параметра:

- JVM-аргумент `-javaagent:/path/to/agent.jar` — путь к собранному JAR агента;
- системное свойство `threading.highlighter.project.dir` — базовый путь, относительно которого создаётся директория `.ij-threading-highlighter/` для trace-файлов.

Пример:

```kotlin
tasks {
    runIde {
        jvmArgs("-javaagent:/path/to/agent.jar")
        systemProperty("threading.highlighter.project.dir", "${project.projectDir}")
    }
}
```

Необязательные системные свойства:

| Свойство | По умолчанию | Назначение |
|---|---|---|
| `threading.highlighter.append.session` | `false` | Режим дозаписи (см. «Модель сессии»). `false` — свежая сессия. |
| `threading.highlighter.max.stack.depth` | `128` | Максимальная глубина захвата стека, во фреймах. |
| `threading.highlighter.flush.interval.minutes` | `15` | Интервал периодического сброса трасс на диск. Полный сброс также выполняется при завершении JVM. |
| `threading.highlighter.min.capture.interval.millis` | `0` | Минимальный интервал между захватами стека для одного маркера. `0` — захват при каждом срабатывании; большее значение снижает нагрузку на частых маркерах ценой полноты данных. |

Фреймы JDK и платформы (`java.*`, `javax.*`, `com.intellij.*` и др.) отфильтровываются агентом на этапе записи.

> Если плагин уже установлен, готовую строку VM-аргументов можно получить через **Tools → Threading Highlighter → Copy Agent VM Options** — путь к агенту вычисляется автоматически.

### 3. Установка плагина

Установите **ZIP-дистрибутив** в рабочую IDE (в которой ведётся разработка анализируемого плагина):

`Settings` → `Plugins` → ⚙️ → `Install Plugin from Disk…` → `plugin/build/distributions/plugin.zip`.

> Устанавливайте `plugin.zip`, а не отдельный `plugin.jar`. ZIP содержит модули `common` и агент; при установке одного `plugin.jar` плагин завершится с `NoClassDefFoundError`.

### 4. Просмотр результатов

После запуска и закрытия sandbox-IDE в рабочей IDE:

1. Трассы перезагружаются автоматически при изменении файлов в `.ij-threading-highlighter/`. При необходимости — **Tools → Threading Highlighter → Reload Threading Traces**.
2. В редакторе отображаются gutter-иконки на строках, попавших в трассы. Если файл редактировался после записи трассы, в подсказке указывается, что номер строки может быть неточным.
3. **Tools → Threading Highlighter → Show Trace Summary** — сводка по загруженным записям (диагностика).
4. **Tools → Threading Highlighter → Hide/Show Threading Markers** — скрытие и показ маркеров без перезагрузки трасс.

### Быстрый старт

Для проверки работоспособности используется модуль `examples`:

```bash
./gradlew :examples:runIde
```

Задача собирает агент, подключает его к запускаемой IDE и задаёт необходимые системные свойства. Выполните один из демонстрационных actions (меню Tools или Find Action) и закройте sandbox-IDE. В рабочей IDE в коде модуля `examples/` появятся gutter-иконки с результатами анализа.
