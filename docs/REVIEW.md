# تقييم المشروع: smart-report-plugin

- **تاريخ المراجعة:** 2026-11-27 (تحديث لمراجعة 2.6.0 التي كُتبت في اليوم نفسه)
- **الإصدار:** 2.8.0 (commit `58308e2`)، ومعه ما دُمج في `master` بعده ولم يصدر بعد: الخط لكل مستند ([#26](https://github.com/MohamedSalah-13/smart-report-plugin/pull/26)) وفاصل الصفحة ودفعات المستندات ([#27](https://github.com/MohamedSalah-13/smart-report-plugin/pull/27))، حتى commit `77c7d12`.
- **ما شُغّل:** `./mvnw verify` محليًا على Windows و JDK 25 بعد دمج `master`: 484 اختبارًا بلا فشل ولا تخطٍّ (api 187، pdf 179، fx 22، excel 56، jasper 40)، ولم يجد japicmp أي تغيير غير متوافق مع v2.8.0. وأُعيدت تجارب القياس: أداء PDF و Excel، وعرض العمود الأول في Excel مع العنوان، والرموز التي لا يحتويها الخط المضمَّن والوضع الصارم، وعنوان Excel يبدأ أو ينتهي بفاصلة عليا. CI أخضر على الوسم v2.8.0 (Ubuntu و Windows مع Java 21 و 25)، وفحص JitPack أخضر له.

## الخلاصة

- **ما هو:** مكتبة Java 21 للتقارير والطباعة، أساسها محرك PDF عربي مبني على PDFBox مع نافذة معاينة JavaFX وطباعة مباشرة ودفعات مستندات، وبجانبه تقرير Excel عام وتقارير Jasper. الحجم نحو 8,300 سطر في 42 صنفًا و5 وحدات Maven.
- **الحكم:** الثغرات التي وجدتها مراجعة 2.6.0 في Excel و Jasper وفي شبكات الأمان أُغلقت في 2.7.0 و 2.8.0، والخط لكل مستند وفاصل الصفحة مدمجان في `master` للإصدار التالي، فلم يبقَ خلل حرج. ما بقي أغلبه في البنية (`PageWriter` والواجهة العامة الموضعية) وفي التوثيق والتوزيع.
- **أهم شيء الآن:** عقد موحد للأجزاء في `PageWriter` قبل أي ميزة تخطيط جديدة؛ فاصل الصفحة دخله فرعًا جديدًا كغيره، واحتاج إصلاحًا من صنف الأخطاء نفسه.

## نقاط القوة

1. **صحة العربي مختبَرة بمواضع الحروف لا بالنص فقط.** أخطاء الاتجاه لا تظهر في النص المستخرج، فاختبارات PDF تقرأ إحداثيات كل حرف وعرضه ([Glyphs.java:16](../smart-report-pdf/src/test/java/com/smart/report/pdf/Glyphs.java#L16)). واختبارات Jasper صارت عربية أيضًا، وتقارن الصندوق الضيق بصندوق يتسع لسطر كامل في PDF و HTML و XLSX.
2. **شبكات أمان للإصدار.** كل بناء يقارن الواجهة الثنائية لكل وحدة بآخر إصدار عبر japicmp ويفشل عند حذف عنصر عام أو تغييره ([pom.xml:187](../pom.xml#L187)). مسار الطباعة يُختبر بطابعة وهمية تستقبل المهمة وتفحص المقاس وعدد الصفحات والنسخ وترتيبها ([PrintToStreamTest.java:52](../smart-report-pdf/src/test/java/com/smart/report/pdf/PrintToStreamTest.java#L52)). CI يعمل على Ubuntu و Windows مع Java 21 و 25 ([ci.yml:29](../.github/workflows/ci.yml#L29)) ويشغّل أمر بناء JitPack نفسه، وبعد كل وسم يتحقق workflow منفصل أن JitPack يقدّم كل الوحدات ([jitpack.yml](../.github/workflows/jitpack.yml))، وخطوات الإصدار مكتوبة في [RELEASING.md](../RELEASING.md). أُضيف هذا الفحص بعد أن خرج 2.7.0 غير قابل للتنزيل من JitPack.
3. **التخطيط يتحمل الحالات الصعبة.** صف أطول من صفحة يُقسَّم ([PageWriter.java:997](../smart-report-pdf/src/main/java/com/smart/report/pdf/PageWriter.java#L997))، والإيصال الحراري يُقاس ثم يُرسم بطوله الفعلي ([PdfDocument.java:451](../smart-report-pdf/src/main/java/com/smart/report/pdf/PdfDocument.java#L451)).
4. **الناتج واحد على كل جهاز بلا إعداد.** في PDF الخط مضمَّن برخصته ولا يحتاج ICU، والخط العادي يغطي كل ASCII والأرقام الهندية وصور الحروف التي يستخدمها `ArabicTextShaper` (تم التحقق بقراءة جدول الخط). وما لا يحتويه من رموز (`²` و `³` و `½` و `±`) يُرسم الآن بـ Liberation Sans الموجود أصلًا داخل PDFBox ([PdfFontResolver.java:82](../smart-report-pdf/src/main/java/com/smart/report/pdf/PdfFontResolver.java#L82)) لا بخط النظام. وفي Jasper تسجّل الوحدة الخط العربي كـ font extension ([ArabicFonts.java:42](../smart-report-jasper/src/main/java/com/smart/report/jasper/ArabicFonts.java#L42))، فيُرسم العربي دون أن يحدد القالب خطًا، ويخرج النص اللاتيني كما كان.
5. **الأخطاء تفشل بصوت عالٍ وبرسالة تقول ماذا تفعل.** أمثلة: باركود أعرض من السطر ([PageWriter.java:784](../smart-report-pdf/src/main/java/com/smart/report/pdf/PageWriter.java#L784))، طابعة غير موجودة مع قائمة المتاح ([PdfPrinter.java:138](../smart-report-pdf/src/main/java/com/smart/report/pdf/PdfPrinter.java#L138))، وحدة ناقصة من الـ classpath ([ReportService.java:75](../smart-report-api/src/main/java/com/smart/report/ReportService.java#L75))، وحرف لا يرسمه أي خط في الوضع الصارم، برسالة فيها الحرف وكوده والنص الذي هو فيه ([FontSet.java:335](../smart-report-pdf/src/main/java/com/smart/report/pdf/FontSet.java#L335)). ونص في قالب Jasper لا يُطبع منه شيء صار يُسجَّل تحذيرًا باسم التقرير والحقل ([ArabicTextMeasurerFactory.java:138](../smart-report-jasper/src/main/java/com/smart/report/jasper/ArabicTextMeasurerFactory.java#L138)).
6. **التقسيم لوحدات حقيقي.** البرنامج الذي يطبع PDF لا يحمل POI ولا Jasper، والربط عبر `ServiceLoader`. ووحدة المعاينة تصل إلى الطابعة من منفذ واحد ([PreviewSystem.java:18](../smart-report-fx/src/main/java/com/smart/report/fx/PreviewSystem.java#L18))، فتستبدله اختباراتها ولا تصل إلى طابعة حقيقية.
7. **الأداء كافٍ والواجهة لا تتجمد.** القياس الحالي (تقرير عام بخمسة أعمدة، أفضل تشغيل بعد الإحماء): PDF يُخرج 1,000 صف في نحو 0.4 ثانية و20,000 صف (646 صفحة) في 3.95 ثانية، و Excel يُخرج 20,000 صف في 0.74 ثانية و100,000 صف في 2.6 ثانية. وفي نافذة المعاينة يجري البناء والرسم خارج thread الواجهة، وتُهمل الرسمات القديمة، ولحجم الصورة سقف ([PreviewWindow.java:441](../smart-report-fx/src/main/java/com/smart/report/fx/PreviewWindow.java#L441)).

## نقاط الضعف

| الخطورة | المعنى |
|---------|--------|
| 🔴 حرجة | خطر على البيانات أو الأمان أو صحة النتائج |
| 🟠 مهمة | تعطل التطوير أو تسبب أخطاء متكررة |
| 🟡 تحسين | جودة وصيانة، لا ضرر عاجل |

### 🔴 حرجة

لا شيء. البند الحرج الوحيد في مراجعة 2.6.0 (العربي يختفي بصمت من PDF الخارج من Jasper) أُصلح في 2.7.0 و 2.8.0، والتفاصيل في [ما تغير منذ المراجعة السابقة](#ما-تغير-منذ-المراجعة-السابقة).

### 🟠 مهمة

**1. قواعد تقسيم الصفحات مكتوبة لكل عنصر على حدة داخل `PageWriter`.**

- **الأثر:** نفس صنف الخطأ («عنصر يخرج عن الورقة أو ينفصل عما يخصه») تكرر حتى 2.6.0: صف أطول من صفحة (2.1.1)، رأس جدول وحقل أطول من صفحة (2.4.0)، خانات توقيع وحدها (2.6.0). وتكرر مع `pageBreak()` نفسه: احتاج قبل دمجه إصلاحًا لصفحة فارغة بعد الفاصل، ولفاصل على الورق الحراري يفصل الحقول وسطور الملخص التي حوله (commit `fb6c39d`). والميزات المقترحة (علامة مائية، ملصقات، صورة في الجسم) ستحتاج كل منها منطقها الخاص.
- **الدليل:** الملف صار 1,179 سطرًا، وكل نوع جزء فرع في `draw` ([PageWriter.java:175](../smart-report-pdf/src/main/java/com/smart/report/pdf/PageWriter.java#L175))، وآخرها فرع الفاصل ([PageWriter.java:207](../smart-report-pdf/src/main/java/com/smart/report/pdf/PageWriter.java#L207)). وارتفاع المجموعة التي تُبقى معًا على صفحة واحدة ما زال يُحسب بفرع لكل نوع جزء ([PageWriter.java:333](../smart-report-pdf/src/main/java/com/smart/report/pdf/PageWriter.java#L333)). تغيّر الملف في 11 من أول 60 commit، ثم في 4 commits للخط لكل مستند وفاصل الصفحة. إصلاح الصفحة الفارغة يحكم من العلامات المرسومة على الصفحة لا من قائمة أنواع، وهذا هو الاتجاه الصحيح، لكنه حالة واحدة.
- **اتجاه الحل:** عقد موحد لكل جزء (قياس، ثم رسم أو تقسيم) تبقى معه قواعد الصفحات في مكان واحد. شرطاه في المراجعة السابقة تحققا: اختبار الطباعة (2.7.0) ودمج فاصل الصفحة.

### 🟡 تحسين

**2. الواجهة العامة records موضعية، والقاعدة التي تتجنب ذلك غير مكتوبة.**

- `PdfStyle` له 18 مكوّنًا موضعيًا، وكل `with*` يعيد كتابتها كلها ([PdfStyle.java:29](../smart-report-pdf/src/main/java/com/smart/report/pdf/PdfStyle.java#L29)).
- في 2.8.0 أخذ `ColumnDefinition` مكوّنًا سادسًا (`total`). بقي الـ constructor القديم صراحةً ([ColumnDefinition.java:53](../smart-report-api/src/main/java/com/smart/report/ColumnDefinition.java#L53)) فمرّ فحص japicmp، لكن أي record pattern يفكك الصنف بخمسة متغيرات لم يعد يُترجم، فاحتاج الإصدار إلى ملاحظة ترقية ([CHANGELOG.md:6](../CHANGELOG.md#L6)). japicmp لا يمنع إلا الكسر الثنائي ([pom.xml:182](../pom.xml#L182)).
- #26 سلك الطريق الآمن: الخط والوضع الصارم طريقتان على `PdfDocument` ([PdfDocument.java:134](../smart-report-pdf/src/main/java/com/smart/report/pdf/PdfDocument.java#L134)) لا مكوّنان في `PdfStyle`، فلم يجد japicmp أي تغيير غير متوافق. لكن هذا اختيار جلسة واحدة لا قاعدة، وقد صدر 11 وسمًا (v2.0.0 إلى v2.8.0) في يومين.
- **اتجاه الحل:** كتابة القاعدة التي اتبعها #26 (الخيارات الجديدة طرق أو builder، لا مكوّنات جديدة في records عامة) في `RELEASING.md` أو دليل للمساهمة.

**3. عنوان Excel يبدأ أو ينتهي بفاصلة عليا يُسقط التقرير كله.**

- **الأثر:** `ReportException` بدل التقرير، لعنوان مثل `'Q1' sales` أو `sales 'Q1'`.
- **الدليل:** اسم الورقة يُؤخذ من العنوان ويُحذف منه `\ / * [ ] : ?` فقط ([ExcelReportProvider.java:531](../smart-report-excel/src/main/java/com/smart/report/excel/ExcelReportProvider.java#L531)). في التجربة رمى العنوانان أعلاه «Sheet names must not begin or end with (')». وبجانب ذلك `SXSSFWorkbook.dispose()` مهجورة في POI 6 ([ExcelReportProvider.java:206](../smart-report-excel/src/main/java/com/smart/report/excel/ExcelReportProvider.java#L206)).
- **اتجاه الحل:** حذف الفواصل العليا من طرفي اسم الورقة، مع اختبار.

**4. وحدة Jasper مثبتة على 6.x، والعربي فيها يحتاج صندوقًا أعلى من المعتاد.**

- قرار التثبيت موثَّق وسببه وجيه ([dependabot.yml:18](../.github/dependabot.yml#L18))، لكنه يجرّ مكتبات قديمة: commons-collections 3.2.2، commons-digester 2.1، jfreechart 1.0.19، ecj 3.21.0 (أُعيد التحقق بـ `dependency:list`).
- سطر Noto Naskh نحو 1.7 من حجم الخط ([README.md:594](../README.md#L594)). الصندوق الأقصر من ذلك يُرسم سطره الأول ما دام السطر لا يتجاوز 1.5 من ارتفاع الصندوق ([ArabicTextMeasurerFactory.java:55](../smart-report-jasper/src/main/java/com/smart/report/jasper/ArabicTextMeasurerFactory.java#L55))، أي ما دام الصندوق نحو 1.14 من حجم الخط أو أعلى. وما دون ذلك لا يُطبع النص، لكن مع تحذير في الـ log لا بصمت. فالقوالب المصممة لخط لاتيني تحتاج مراجعة ارتفاع حقولها العربية.
- **اتجاه الحل:** خطة ترحيل إلى Jasper 7 كإصدار رئيسي، والـ font extension جاهز لها.

**5. الحرف الذي لا يرسمه أي خط ما زال يُطبع `?` افتراضيًا.**

- أغلب البند السابق أُصلح في #26: خط لكل مستند، وخط احتياطي للرموز، ووضع صارم. الباقي أن الوضع الصارم مقفول ما لم يُفتح ([PdfDocument.java:144](../smart-report-pdf/src/main/java/com/smart/report/pdf/PdfDocument.java#L144))، فالحرف الغائب عن كل الخطوط يصير `?` ([FontSet.java:262](../smart-report-pdf/src/main/java/com/smart/report/pdf/FontSet.java#L262)) مع تحذير في الـ log فقط ([FontSet.java:344](../smart-report-pdf/src/main/java/com/smart/report/pdf/FontSet.java#L344)). والمكتبة تعتمد على `slf4j-api` وحده، فالبرنامج الذي لم يضع backend لا يرى التحذير.
- في التجربة على هذا الجهاز: `م³` و `½` و `±` و `²` و `×` و `÷` و `°` و `€` تُرسم كلها، أما وجه تعبيري (U+1F600) وحرف صيني (U+4E2D) فيصيران `?` في الوضع العادي ويرميان `ReportException` في الوضع الصارم.
- الخط الافتراضي ما زال إعدادًا عامًا للـ JVM ([PdfFontResolver.java:160](../smart-report-pdf/src/main/java/com/smart/report/pdf/PdfFontResolver.java#L160))، لكن خط المستند يتقدم عليه.
- **اتجاه الحل:** توصية صريحة في README بالوضع الصارم للفواتير، وجعله الافتراضي في الإصدار الرئيسي التالي.

**6. التوثيق للغرباء والتوزيع.**

- Javadoc الواجهة العامة بالعامية المصرية فقط (مثلًا [PdfDocument.java:296](../smart-report-pdf/src/main/java/com/smart/report/pdf/PdfDocument.java#L296))، ولا يُبنى javadoc jar، بل sources jar فقط ([pom.xml:166](../pom.xml#L166)).
- التوزيع على JitPack وحده. صار بناؤه يُفحص بعد كل وسم، لكن 2.7.0 خرج غير قابل للتنزيل واحتاج 2.7.1 ([CHANGELOG.md:23](../CHANGELOG.md#L23))، فالمصدر الواحد نقطة فشل واحدة.
- لا issues على GitHub: القيود المعروفة مكتوبة في هذا الملف و README فقط.

**الأمان:** لا شيء يستحق بندًا. لا أسرار في المستودع، ونصوص Excel تُكتب كنص لا كصيغ (والصيغة الوحيدة `SUM` يبنيها الكود من أرقام الصفوف). الملاحظة الوحيدة أن قوالب Jasper كود يُنفَّذ (وملف `.jasper` يُحمَّل بـ deserialization)، فتُعامل كمصدر موثوق فقط.

## أفكار التطوير

الافتراض: الجمهور برامج سطح مكتب محاسبية وتجارية عربية (مثل naqlat)، في مصر ثم الخليج. إن كان الهدف مختلفًا فترتيب الميزات يتغير. المنجز من أفكار المراجعة السابقة منقول إلى [ما تغير منذ المراجعة السابقة](#ما-تغير-منذ-المراجعة-السابقة).

### تحسينات تقنية

| الفكرة | من يستفيد ولماذا | الأثر | الجهد | تعتمد على |
|---|---|---|---|---|
| كتابة قاعدة تطوير الواجهة: خيارات جديدة بطرق أو builder لا بمكوّنات records | البرامج المعتمدة على المكتبة: لا كسر مصدري في إصدار فرعي | متوسط | صغير | — |
| حذف الفاصلة العليا من طرفي اسم ورقة Excel | كل من يقتبس كلمة في عنوان التقرير | منخفض | صغير | — |
| عقد موحد للأجزاء في `PageWriter` | المطوّر: عنصر جديد بلا منطق صفحات خاص | عالٍ | كبير | — |
| النشر على Maven Central مع javadoc jar | شركات تمنع JitPack، ومن يقرأ التوثيق بالإنجليزية | متوسط | متوسط | groupId جديد |
| الترحيل إلى Jasper 7 | أمان واستمرارية الوحدة | متوسط | كبير | font extension (موجود منذ 2.7.0) |

### ميزات للمنتج

| الفكرة | من يستفيد ولماذا | الأثر | الجهد | تعتمد على |
|---|---|---|---|---|
| إجماليات فرعية في التقرير العام بالتجميع حسب عمود | المحاسب: كشف مجمّع حسب العميل أو الشهر. الإجمالي الكلي موجود منذ 2.8.0 ([Totals.java:34](../smart-report-api/src/main/java/com/smart/report/util/Totals.java#L34)) ولا تجميع بعد | عالٍ | متوسط | — |
| ملصقات باركود للأصناف: EAN-13 وورق ملصقات صغير | المحلات والمخازن. الباركود الحالي Code 128 فقط، والمحرك يرفض الصفحات الصغيرة جدًا ([PageWriter.java:130](../smart-report-pdf/src/main/java/com/smart/report/pdf/PageWriter.java#L130)) | عالٍ | متوسط | عقد الأجزاء |
| علامة مائية أو ختم («مسودة»، «صورة»، «ملغاة»، «مدفوع») | نسخ الفواتير وصور المستندات | متوسط | صغير | — |
| خيار الأرقام الهندية (١٢٣) للمستند كله | مستندات رسمية مصرية. الخط يحتويها | متوسط | متوسط | مراجعة عزل الأرقام في الـ bidi |
| تنسيق الخلية (سالب بالأحمر، خط عريض) وصورة في الجسم (ختم أو توقيع) | فواتير وكشوف أوضح | متوسط | متوسط | عقد الأجزاء |
| نطاق صفحات والطباعة على الوجهين | كشوف طويلة ودفعات كبيرة. لا `PageRanges` ولا `Sides` في مسار الطباعة، أما Ctrl+P وترتيب النسخ فموجودان | منخفض | صغير | — |

## التوصية

1. **عقد الأجزاء في `PageWriter` قبل أي ميزة تخطيط أخرى.** فاصل الصفحة دخل فرعًا جديدًا واحتاج إصلاحًا من صنف الأخطاء نفسه قبل دمجه، والعلامة المائية والملصقات والصورة في الجسم وتنسيق الخلية كلها تمر من هناك. شرطا هذا العمل (اختبار الطباعة ودمج الفاصل) تحققا.
2. **كتابة قاعدة تطوير الواجهة العامة.** #26 اتبعها عمليًا فمرّ بلا أي تغيير غير متوافق، بينما احتاج 2.8.0 ملاحظة ترقية لأن `ColumnDefinition` أخذ مكوّنًا جديدًا. الإصدارات سريعة (11 وسمًا في يومين)، والقاعدة المكتوبة أرخص من ملاحظة ترقية في كل مرة.
3. **إصلاح اسم ورقة Excel.** عيب مؤكد يُسقط التقرير كله، وإصلاحه في سطر واحد.

وما دُمج في `master` (#26 و #27) ينتظر إصدارًا، ويمكن أن يدخل إصلاح Excel معه.

## ما تغير منذ المراجعة السابقة

مراجعة 2.6.0 (commit `e997c69`) وجدت بندًا حرجًا وأربعة مهمة وأربعة تحسينات. الحالة الآن (و«بعد 2.8.0» تعني مدمجًا في `master` للإصدار التالي ولم يصدر بعد):

| البند في مراجعة 2.6.0 | الحالة | الإصدار | PR |
|---|---|---|---|
| 🔴 العربي يختفي بصمت من PDF الخارج من Jasper | ✅ Noto Naskh كـ font extension افتراضي مع اختبارات عربية، ثم السطر الأول في الصندوق الأقصر قليلًا من سطره مع تحذير حين لا يُطبع نص | 2.7.0، 2.8.0 | [#20](https://github.com/MohamedSalah-13/smart-report-plugin/pull/20)، [#24](https://github.com/MohamedSalah-13/smart-report-plugin/pull/24) |
| 🟠 عنوان Excel يمطّ العمود الأول | ✅ العنوان خارج حساب العروض. القياس: عرض العمود الأول 1,794 وحدة بعنوان طويل وبدونه (كان 15,959 مقابل 1,153) | 2.7.0 | [#18](https://github.com/MohamedSalah-13/smart-report-plugin/pull/18) |
| 🟠 تتبّع عرض الأعمدة يبطّئ Excel | ✅ العرض من عينة أول 500 صف. القياس: 20,000 صف في 0.74 ثانية و100,000 في 2.6 ثانية (كانت 11.3 و48.5) | 2.7.0 | [#18](https://github.com/MohamedSalah-13/smart-report-plugin/pull/18) |
| 🟠 مسار الطباعة بلا اختبار و CI على Linux فقط | ✅ اختبار يطبع إلى طابعة وهمية، و Windows في مصفوفة CI | 2.7.0 | [#17](https://github.com/MohamedSalah-13/smart-report-plugin/pull/17) |
| 🟠 قواعد الصفحات داخل `PageWriter` | ⏳ باقٍ، وهو البند 1 الآن، ودخله فاصل الصفحة | — | — |
| 🟡 الخط إعداد عام والحرف الناقص `?` | ✅ في أغلبه: `PdfDocument.font(PdfFont)`، و Liberation Sans احتياطيًا للرموز، و `failOnMissingGlyphs(true)`، وإهمال المحارف التي لا شكل لها مثل U+FE0F، وتحذير حين يخلو خط المستند من العربي. الباقي أن الوضع الصارم ليس الافتراضي (البند 5) | بعد 2.8.0 | [#26](https://github.com/MohamedSalah-13/smart-report-plugin/pull/26) |
| 🟡 records موضعية ولا فحص توافق | جزئي: فحص japicmp مقابل آخر إصدار ✅، والـ records الموضعية باقية (البند 2) | 2.7.0 | [#17](https://github.com/MohamedSalah-13/smart-report-plugin/pull/17) |
| 🟡 Jasper مثبَّت على 6.x | ⏳ باقٍ (البند 4) | — | — |
| 🟡 التوثيق والتوزيع | جزئي: Maven wrapper ✅ (2.7.1)، و CI بـ `./mvnw` مع أمر بناء JitPack ✅، وفحص JitPack بعد كل وسم و `RELEASING.md` ✅. javadoc jar و Maven Central باقيان (البند 6) | 2.7.1، 2.8.0 | [#21](https://github.com/MohamedSalah-13/smart-report-plugin/pull/21)، [#22](https://github.com/MohamedSalah-13/smart-report-plugin/pull/22) |

ومن أفكار التطوير السابقة:

| الفكرة | الحالة | الإصدار | PR |
|---|---|---|---|
| صف إجمالي في التقرير العام (`withTotal`): مجموع في PDF وصيغة `SUM` في Excel | ✅ | 2.8.0 | [#23](https://github.com/MohamedSalah-13/smart-report-plugin/pull/23) |
| تثبيت صف الرأس و autofilter في Excel | ✅ | 2.7.0 | [#18](https://github.com/MohamedSalah-13/smart-report-plugin/pull/18) |
| طباعة مباشرة على الطابعة المحفوظة (`printOrShow`)، و Ctrl+P / Ctrl+S في المعاينة | ✅ | 2.7.0 | [#16](https://github.com/MohamedSalah-13/smart-report-plugin/pull/16) |
| رمز QR للمرحلة الثانية من ZATCA | ✅ `ZatcaQr.phase2`، والحقول 6 إلى 9 تُستلم جاهزة | 2.7.0 | [#19](https://github.com/MohamedSalah-13/smart-report-plugin/pull/19) |
| تفقيط إنجليزي وعملات خليجية (QAR, BHD, OMR, JOD) | ✅ | 2.7.0 | [#19](https://github.com/MohamedSalah-13/smart-report-plugin/pull/19) |
| `pageBreak()` وطباعة دفعة مستندات في مهمة واحدة | ✅ `PdfDocument.pageBreak()`، و `PdfBatch`: عدة مستندات في PDF واحد ومهمة طباعة واحدة، لكل منها ترقيم صفحاته، مع نسخ مرتبة ومعاينة للدفعة (`PdfPreview.ofBatch`) | بعد 2.8.0 | [#27](https://github.com/MohamedSalah-13/smart-report-plugin/pull/27) |
| خط لكل مستند ووضع صارم للحروف الناقصة | ✅ (انظر بند الخط في الجدول السابق) | بعد 2.8.0 | [#26](https://github.com/MohamedSalah-13/smart-report-plugin/pull/26) |

وظهر في هذه المراجعة بندان جديدان: اسم ورقة Excel (البند 3)، وحد الصندوق العربي في Jasper (ضمن البند 4).

## ما لم يُفحص

- لم تُجرَّب الطباعة على طابعة حقيقية (A4 أو حرارية)، ولا المكتبة على module path ولا على Linux بلا خطوط نظام. ولم يُفتح ملف Excel الناتج في Excel نفسه (قُرئ بـ POI)، ولم يُجرَّب Jasper بقوالب برامج حقيقية.
- من #26 و #27 قُرئ ما يخص بنود هذا التقرير (`FontSet` و `PdfDocument` و `PageWriter`)، وجُرّبت الرموز والوضع الصارم، أما `PdfBatch` ومعاينة الدفعة فحكمهما من اختباراتهما فقط، ولم يُجرَّبا يدويًا ولا على طابعة.
- أرقام أداء PDF لا تُقارن مباشرة بمراجعة 2.6.0، لأن بيانات القياس السابق لم تُحفظ؛ أرقام Excel مقيسة بنفس الحجمين.
- بناء JitPack للوسم v2.8.0 مأخوذ من نتيجة workflow الفحص، ولم تُنزَّل الحزم يدويًا. ولم تُراجع `README.ar.md` و `SKILL.md` سطرًا سطرًا، ولم تُقرأ كل ملفات الاختبار كاملة.
