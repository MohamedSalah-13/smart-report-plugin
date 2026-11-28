# تقييم المشروع: smart-report-plugin

- **تاريخ المراجعة:** 2026-11-28 (تحديث لحالة البنود بعد 2.9.1 و 2.10.0، لا مراجعة جديدة من الصفر؛ المراجعة الكاملة السابقة كُتبت على 2.8.0 في 2026-11-27)
- **الإصدار:** 2.10.0 (الوسم `v2.10.0` على commit `47acf39`)، وفيه ما بعد 2.9.0: إصلاح اسم ورقة Excel ([#29](https://github.com/MohamedSalah-13/smart-report-plugin/pull/29))، وعقد الأجزاء في `PageWriter` ([#30](https://github.com/MohamedSalah-13/smart-report-plugin/pull/30))، والعلامة المائية ([#31](https://github.com/MohamedSalah-13/smart-report-plugin/pull/31))، والإجمالي الفرعي لكل مجموعة ([#32](https://github.com/MohamedSalah-13/smart-report-plugin/pull/32)).
- **ما شُغّل:** `./mvnw verify` محليًا على Windows و JDK 25 على الوسم: 577 اختبارًا بلا فشل ولا تخطٍّ (api 192، pdf 243، fx 22، excel 80، jasper 40)، ولم يجد japicmp أي تغيير غير متوافق مع v2.9.1. ورُسمت صفحات العلامة المائية فوق مربع الملخص وعلى الورق الحراري، والكشف المجمّع بإجماليه الكلي. CI أخضر على #31 و #32 (Ubuntu و Windows مع Java 21 و 25)، وفحص JitPack أخضر للوسم v2.10.0 مع تنزيل jar كل وحدة.
- **أفكار مضافة (2026-11-28):** 12 فكرة من قراءة كود الطباعة في البرامج التي تستخدم المكتبة أو يمكن أن تستخدمها، من [#43](https://github.com/MohamedSalah-13/smart-report-plugin/issues/43) إلى [#54](https://github.com/MohamedSalah-13/smart-report-plugin/issues/54). هذه قراءة لكود تلك البرامج، لا مراجعة جديدة للمكتبة، ولم يُشغَّل لها بناء ولا اختبار.

## الخلاصة

- **ما هو:** مكتبة Java 21 للتقارير والطباعة، أساسها محرك PDF عربي مبني على PDFBox مع نافذة معاينة JavaFX وطباعة مباشرة ودفعات مستندات، وبجانبه تقرير Excel عام وتقارير Jasper. الحجم نحو 9,400 سطر في 56 صنفًا و5 وحدات Maven.
- **الحكم:** البنود الثلاثة التي تصدّرت المراجعة السابقة أُغلقت: قواعد الصفحات صارت في مكان واحد على عقد موحد للأجزاء (2.9.1)، وقاعدة تطوير الواجهة مكتوبة في `RELEASING.md`، واسم ورقة Excel أُصلح (2.9.1). ثم صدرت عليها ميزتان من أفكار المراجعة (2.10.0). لم يبقَ بند حرج ولا مهم؛ ما بقي تحسينات في Jasper والحروف الناقصة والتوثيق والتوزيع.
- **أهم شيء الآن:** ميزات المنتج التي كانت تنتظر عقد الأجزاء، وأولها ملصقات الباركود.

## نقاط القوة

1. **صحة العربي مختبَرة بمواضع الحروف لا بالنص فقط.** أخطاء الاتجاه لا تظهر في النص المستخرج، فاختبارات PDF تقرأ إحداثيات كل حرف وعرضه ([Glyphs.java:16](../smart-report-pdf/src/test/java/com/smart/report/pdf/Glyphs.java#L16)). واختبارات Jasper عربية أيضًا، وتقارن الصندوق الضيق بصندوق يتسع لسطر كامل في PDF و HTML و XLSX.
2. **شبكات أمان للإصدار والتخطيط.** كل بناء يقارن الواجهة الثنائية لكل وحدة بآخر إصدار عبر japicmp ويفشل عند حذف عنصر عام أو تغييره ([pom.xml:187](../pom.xml#L187)). مسار الطباعة يُختبر بطابعة وهمية تستقبل المهمة وتفحص المقاس وعدد الصفحات والنسخ وترتيبها ([PrintToStreamTest.java:52](../smart-report-pdf/src/test/java/com/smart/report/pdf/PrintToStreamTest.java#L52)). ومواضع العناصر في 20 مستندًا مرجعيًا محفوظة كلقطات تُقارن بدقة 0.01 نقطة ([LayoutSnapshotTest.java:28](../smart-report-pdf/src/test/java/com/smart/report/pdf/LayoutSnapshotTest.java#L28))، سُجّلت على الكود القديم قبل إعادة كتابة `PageWriter` ومرّت بعدها كما هي. CI يعمل على Ubuntu و Windows مع Java 21 و 25 ([ci.yml:29](../.github/workflows/ci.yml#L29)) ويشغّل أمر بناء JitPack نفسه، وبعد كل وسم يتحقق workflow منفصل أن JitPack يقدّم كل الوحدات ([jitpack.yml](../.github/workflows/jitpack.yml))، وخطوات الإصدار وقاعدة تطوير الواجهة مكتوبة في [RELEASING.md](../RELEASING.md#L10).
3. **قواعد الصفحات مكتوبة مرة واحدة.** كل جزء يصف قطعه فقط (طولها وهل تنقسم) ([Part.java:12](../smart-report-pdf/src/main/java/com/smart/report/pdf/Part.java#L12))، و `PageWriter` يضعها كلها بحلقة واحدة: صفحة جديدة حين لا تكفي القطعة، وتقسيم الأطول من صفحة، ورأس الجدول في كل صفحة ([PageWriter.java:231](../smart-report-pdf/src/main/java/com/smart/report/pdf/PageWriter.java#L231)). الملف نزل من 1,179 سطرًا إلى 474 بعد أن انتقل رسم كل جزء إلى صنفه (`TablePart` و `Parts` و `SymbolParts`)، ودخل بعده الإجمالي الفرعي (قطعة تبقى مع الصف الذي فوقها) والعلامة المائية دون قاعدة صفحات جديدة. والإيصال الحراري يُقاس ثم يُرسم بطوله الفعلي ([PageWriter.java:109](../smart-report-pdf/src/main/java/com/smart/report/pdf/PageWriter.java#L109)).
4. **الناتج واحد على كل جهاز بلا إعداد.** في PDF الخط مضمَّن برخصته ولا يحتاج ICU، والخط العادي يغطي كل ASCII والأرقام الهندية وصور الحروف التي يستخدمها `ArabicTextShaper`. وما لا يحتويه من رموز (`²` و `³` و `½` و `±`) يُرسم بـ Liberation Sans الموجود أصلًا داخل PDFBox ([PdfFontResolver.java:82](../smart-report-pdf/src/main/java/com/smart/report/pdf/PdfFontResolver.java#L82)) لا بخط النظام. وفي Jasper تسجّل الوحدة الخط العربي كـ font extension ([ArabicFonts.java:42](../smart-report-jasper/src/main/java/com/smart/report/jasper/ArabicFonts.java#L42))، فيُرسم العربي دون أن يحدد القالب خطًا.
5. **الأخطاء تفشل بصوت عالٍ وبرسالة تقول ماذا تفعل.** أمثلة: باركود أعرض من السطر ([SymbolParts.java:97](../smart-report-pdf/src/main/java/com/smart/report/pdf/SymbolParts.java#L97))، طابعة غير موجودة مع قائمة المتاح ([PdfPrinter.java:138](../smart-report-pdf/src/main/java/com/smart/report/pdf/PdfPrinter.java#L138))، وحدة ناقصة من الـ classpath ([ReportService.java:75](../smart-report-api/src/main/java/com/smart/report/ReportService.java#L75))، وحرف لا يرسمه أي خط في الوضع الصارم ([FontSet.java:335](../smart-report-pdf/src/main/java/com/smart/report/pdf/FontSet.java#L335))، وتقرير مجمّع بلا عمود إجمالي. ونص في قالب Jasper لا يُطبع منه شيء يُسجَّل تحذيرًا باسم التقرير والحقل ([ArabicTextMeasurerFactory.java:138](../smart-report-jasper/src/main/java/com/smart/report/jasper/ArabicTextMeasurerFactory.java#L138)).
6. **التقسيم لوحدات حقيقي.** البرنامج الذي يطبع PDF لا يحمل POI ولا Jasper، والربط عبر `ServiceLoader`. ووحدة المعاينة تصل إلى الطابعة من منفذ واحد ([PreviewSystem.java:18](../smart-report-fx/src/main/java/com/smart/report/fx/PreviewSystem.java#L18))، فتستبدله اختباراتها ولا تصل إلى طابعة حقيقية.
7. **الأداء كافٍ والواجهة لا تتجمد.** إعادة كتابة `PageWriter` لم تغيّر الأداء تقريبًا: تقرير عام بخمسة أعمدة و20,000 صف خرج في 3.8 إلى 3.9 ثانية قبلها وفي نحو 4.0 بعدها، بحجم الملف نفسه. و Excel يُخرج 20,000 صف في أقل من ثانية. وفي نافذة المعاينة يجري البناء والرسم خارج thread الواجهة، ولحجم الصورة سقف ([PreviewWindow.java:441](../smart-report-fx/src/main/java/com/smart/report/fx/PreviewWindow.java#L441)).

## نقاط الضعف

| الخطورة | المعنى |
|---------|--------|
| 🔴 حرجة | خطر على البيانات أو الأمان أو صحة النتائج |
| 🟠 مهمة | تعطل التطوير أو تسبب أخطاء متكررة |
| 🟡 تحسين | جودة وصيانة، لا ضرر عاجل |

### 🔴 حرجة

لا شيء.

### 🟠 مهمة

لا شيء. البند المهم الوحيد في المراجعة السابقة (قواعد الصفحات لكل عنصر على حدة) أُغلق في 2.9.1، والتفاصيل في [ما تغير منذ المراجعة السابقة](#ما-تغير-منذ-المراجعة-السابقة).

### 🟡 تحسين

**1. وحدة Jasper مثبتة على 6.x، والعربي فيها يحتاج صندوقًا أعلى من المعتاد.**

- قرار التثبيت موثَّق وسببه وجيه ([dependabot.yml:18](../.github/dependabot.yml#L18))، لكنه يجرّ مكتبات قديمة: commons-collections 3.2.2، commons-digester 2.1، jfreechart 1.0.19، ecj 3.21.0.
- سطر Noto Naskh نحو 1.7 من حجم الخط ([README.md:677](../README.md#L677)). الصندوق الأقصر من ذلك يُرسم سطره الأول ما دام السطر لا يتجاوز 1.5 من ارتفاع الصندوق ([ArabicTextMeasurerFactory.java:55](../smart-report-jasper/src/main/java/com/smart/report/jasper/ArabicTextMeasurerFactory.java#L55))، وما دون ذلك لا يُطبع النص، مع تحذير في الـ log. فالقوالب المصممة لخط لاتيني تحتاج مراجعة ارتفاع حقولها العربية.
- **اتجاه الحل:** خطة ترحيل إلى Jasper 7 كإصدار رئيسي، والـ font extension جاهز لها ([#37](https://github.com/MohamedSalah-13/smart-report-plugin/issues/37)).

**2. الحرف الذي لا يرسمه أي خط ما زال يُطبع `?` افتراضيًا.**

- الوضع الصارم مقفول ما لم يُفتح ([PdfDocument.java:145](../smart-report-pdf/src/main/java/com/smart/report/pdf/PdfDocument.java#L145))، فالحرف الغائب عن كل الخطوط (وجه تعبيري، حرف صيني) يصير `?` ([FontSet.java:262](../smart-report-pdf/src/main/java/com/smart/report/pdf/FontSet.java#L262)) مع تحذير في الـ log فقط ([FontSet.java:344](../smart-report-pdf/src/main/java/com/smart/report/pdf/FontSet.java#L344)). والمكتبة تعتمد على `slf4j-api` وحده، فالبرنامج الذي لم يضع backend لا يرى التحذير.
- **اتجاه الحل:** توصية صريحة في README بالوضع الصارم للفواتير، وجعله الافتراضي في الإصدار الرئيسي التالي ([#38](https://github.com/MohamedSalah-13/smart-report-plugin/issues/38)).

**3. التوثيق للغرباء والتوزيع.**

- Javadoc الواجهة العامة بالعامية المصرية فقط (مثلًا [PdfDocument.java:296](../smart-report-pdf/src/main/java/com/smart/report/pdf/PdfDocument.java#L296))، ولا يُبنى javadoc jar، بل sources jar فقط ([pom.xml:166](../pom.xml#L166)).
- التوزيع على JitPack وحده. صار بناؤه يُفحص بعد كل وسم، لكن 2.7.0 خرج غير قابل للتنزيل واحتاج 2.7.1 ([CHANGELOG.md:60](../CHANGELOG.md#L60))، فالمصدر الواحد نقطة فشل واحدة.
- **اتجاه الحل:** javadoc بالإنجليزية و javadoc jar، ثم Maven Central ([#36](https://github.com/MohamedSalah-13/smart-report-plugin/issues/36)).

وكل ما بقي من هذا الملف صار issue على GitHub، فيُتابع هناك.

**4. الـ records العامة ما زالت موضعية.**

- `PdfStyle` له 18 مكوّنًا موضعيًا ([PdfStyle.java:29](../smart-report-pdf/src/main/java/com/smart/report/pdf/PdfStyle.java#L29)). القاعدة المكتوبة الآن تمنع إضافة مكوّن جديد إليه أو إلى غيره، و 2.9.0 و 2.10.0 اتبعاها، فلا ضرر ما دامت القاعدة متَّبعة. لكن japicmp لا يفحصها ([pom.xml:182](../pom.xml#L182))، فهي مسؤولية المراجع.

**الأمان:** لا شيء يستحق بندًا. لا أسرار في المستودع، ونصوص Excel تُكتب كنص لا كصيغ (والصيغ الوحيدة، `SUM` و `SUBTOTAL`، يبنيها الكود من أرقام الصفوف). الملاحظة الوحيدة أن قوالب Jasper كود يُنفَّذ (وملف `.jasper` يُحمَّل بـ deserialization)، فتُعامل كمصدر موثوق فقط.

## أفكار التطوير

الافتراض: الجمهور برامج سطح مكتب محاسبية وتجارية عربية (مثل naqlat)، في مصر ثم الخليج. إن كان الهدف مختلفًا فترتيب الميزات يتغير. المنجز من أفكار المراجعات السابقة منقول إلى [ما تغير منذ المراجعة السابقة](#ما-تغير-منذ-المراجعة-السابقة).

### تحسينات تقنية

| الفكرة | من يستفيد ولماذا | الأثر | الجهد | تعتمد على |
|---|---|---|---|---|
| النشر على Maven Central مع javadoc jar ([#36](https://github.com/MohamedSalah-13/smart-report-plugin/issues/36)) | شركات تمنع JitPack، ومن يقرأ التوثيق بالإنجليزية | متوسط | متوسط | groupId جديد |
| الترحيل إلى Jasper 7 ([#37](https://github.com/MohamedSalah-13/smart-report-plugin/issues/37)) | أمان واستمرارية الوحدة | متوسط | كبير | font extension (موجود منذ 2.7.0) |
| الوضع الصارم للحروف افتراضيًا ([#38](https://github.com/MohamedSalah-13/smart-report-plugin/issues/38)) | الفواتير: لا `?` مطبوعة دون أن يعرف أحد | متوسط | صغير | إصدار رئيسي |

### ميزات للمنتج

| الفكرة | من يستفيد ولماذا | الأثر | الجهد | تعتمد على |
|---|---|---|---|---|
| ملصقات باركود للأصناف: EAN-13 وورق ملصقات صغير ([#33](https://github.com/MohamedSalah-13/smart-report-plugin/issues/33)) | المحلات والمخازن. الباركود الحالي Code 128 فقط، والهوامش على الورق الصغير جدًا لا تترك مكانًا ([PageWriter.java:98](../smart-report-pdf/src/main/java/com/smart/report/pdf/PageWriter.java#L98)) | عالٍ | متوسط | عقد الأجزاء ✅ |
| تنسيق الخلية (سالب بالأحمر، خط عريض) ([#34](https://github.com/MohamedSalah-13/smart-report-plugin/issues/34)) وصورة في الجسم (ختم أو توقيع) ([#35](https://github.com/MohamedSalah-13/smart-report-plugin/issues/35)) | فواتير وكشوف أوضح | متوسط | متوسط | عقد الأجزاء ✅ |
| خيار الأرقام الهندية (١٢٣) للمستند كله ([#39](https://github.com/MohamedSalah-13/smart-report-plugin/issues/39)) | مستندات رسمية مصرية. الخط يحتويها | متوسط | متوسط | مراجعة عزل الأرقام في الـ bidi |
| رأس لكل مجموعة في الكشف المجمّع، أو بداية كل مجموعة في صفحة ([#40](https://github.com/MohamedSalah-13/smart-report-plugin/issues/40)) | كشف لعميل واحد في كل صفحة يُرسل له | منخفض | صغير | الإجمالي الفرعي ✅ |
| نطاق صفحات والطباعة على الوجهين ([#41](https://github.com/MohamedSalah-13/smart-report-plugin/issues/41)) | كشوف طويلة ودفعات كبيرة. لا `PageRanges` ولا `Sides` في مسار الطباعة | منخفض | صغير | — |

### أفكار من كود الطباعة في البرامج المستخدِمة

أُضيفت في 2026-11-28. مصدرها ما كتبته برامج سطح المكتب حول الطباعة بنفسها: ما تكرر في أكثر من برنامج، أو ما اضطر برنامج إلى كتابته فوق المكتبة.

| الفكرة | من يستفيد ولماذا | الأثر | الجهد | تعتمد على |
|---|---|---|---|---|
| إعداد طباعة لكل نوع مستند: المقاس والإخراج والطابعة والنسخ، ولوحة إعدادات جاهزة ([#43](https://github.com/MohamedSalah-13/smart-report-plugin/issues/43)) | كل برنامج يطبع أكثر من ورقة. اليوم `remember(key)` يحفظ آخر طابعة ومقاس فقط، وبرنامجان كتبا الباقي كلٌّ بطريقته | عالٍ | متوسط | — |
| طريق طباعة واحد يطبّق الإعداد: مباشر، معاينة، حفظ، اسأل، و«لمرة واحدة»، وتنبيه عند غياب الطابعة ([#44](https://github.com/MohamedSalah-13/smart-report-plugin/issues/44)) | الكاشير والمكتب. `printOrShow` يغطي المباشر والمعاينة فقط | عالٍ | متوسط | #43 |
| مستندات جاهزة: فاتورة بأكثر من شكل، إيصال اسم الصنف فيه سطر مستقل، كشف حساب، سند ([#46](https://github.com/MohamedSalah-13/smart-report-plugin/issues/46)) | كل برنامج محاسبي. اليوم يركّب كل برنامج مستنده من `PdfDocument` بيده | عالٍ | كبير | عقد الأجزاء ✅ |
| شكل التقرير كإعداد يختاره المحل مرة: مجموعات ألوان، سطر «طُبع في / بواسطة»، ترقيم `1 / 3`، مسافة علوية للورق المطبوع مسبقًا ([#45](https://github.com/MohamedSalah-13/smart-report-plugin/issues/45)) | محلات لها ورق بترويسة مطبوعة، وبرامج تعرض شاشة «شكل التقارير» | متوسط | متوسط | دوال `with…`، لا مكوّن جديد في `PdfStyle` |
| الطابعات الحرارية: مهمة لكل نسخة على اللفة، فتح درج النقدية، وسؤال الطابعة عن المقاس الذي تدعمه ([#47](https://github.com/MohamedSalah-13/smart-report-plugin/issues/47)) | نقاط البيع. النسخ اليوم بسمة `Copies` لكل الورق ([PdfPrinter.java:109](../smart-report-pdf/src/main/java/com/smart/report/pdf/PdfPrinter.java#L109)) | متوسط | متوسط | تجربة على طابعة حقيقية ([#42](https://github.com/MohamedSalah-13/smart-report-plugin/issues/42)) |
| وحدات الباركود و QR بعدد صحيح من نقاط الطابعة ([#48](https://github.com/MohamedSalah-13/smart-report-plugin/issues/48)) | الإيصالات والملصقات. الوحدة اليوم 0.33 مم، أي 2.64 نقطة على رأس 203 dpi ([SymbolParts.java:22](../smart-report-pdf/src/main/java/com/smart/report/pdf/SymbolParts.java#L22)). خطر محتمل لا عيب مؤكَّد | متوسط | صغير | مرتبطة بـ #33 |
| المعاينة لأي مصدر (PDF جاهز أو `JasperPrint`)، وسطر تنبيه، وأيقونة النافذة الأم ([#51](https://github.com/MohamedSalah-13/smart-report-plugin/issues/51)) | برامج تطبع بـ Jasper وتريد النافذة نفسها | متوسط | متوسط | — |
| التفقيط: 12 عملة إضافية، وخيار للمبلغ السالب ([#50](https://github.com/MohamedSalah-13/smart-report-plugin/issues/50)) | برامج خارج العملات التسع الحالية، وكشوف برصيد دائن | متوسط | صغير | — |
| PDF/A-3 مع ملف مرفق: XML الفاتورة الإلكترونية داخل الـ PDF ([#52](https://github.com/MohamedSalah-13/smart-report-plugin/issues/52)) | الفوترة الإلكترونية وأرشفتها | متوسط | متوسط | veraPDF في الاختبار |
| كود مستند يُقرأ بالماسح: أرقام فقط، طول ثابت، خانة تحقق ([#49](https://github.com/MohamedSalah-13/smart-report-plugin/issues/49)) | المرتجعات والبحث عن فاتورة بمسح ورقتها | منخفض | صغير | — |
| QR والباركود كصورة PNG ([#53](https://github.com/MohamedSalah-13/smart-report-plugin/issues/53)) | برامج تعرض QR على الشاشة أو من خادم ويب | منخفض | صغير | — |
| اسم ملف آمن من المعاينة، وصفحة بالعرض للتقرير العام كثير الأعمدة ([#54](https://github.com/MohamedSalah-13/smart-report-plugin/issues/54)) | كل من يحفظ من المعاينة بعنوان فيه `/` أو `:` | منخفض | صغير | — |

وما يحتاجه ملصق الصنف عمليًّا (ملصق مزدوج، سياسة الاسم الطويل، سعر قديم مشطوب، معايرة لكل طابعة) أُضيف تعليقًا على [#33](https://github.com/MohamedSalah-13/smart-report-plugin/issues/33).

## التوصية

1. **ملصقات الباركود (EAN-13 وورق الملصقات)، [#33](https://github.com/MohamedSalah-13/smart-report-plugin/issues/33).** أعلى أثر بين ما بقي، وكانت تنتظر عقد الأجزاء، وهو جاهز الآن: الملصق جزء جديد يكتب قطعه ولا يلمس قاعدة صفحات.
2. **تنسيق الخلية وصورة في الجسم، [#34](https://github.com/MohamedSalah-13/smart-report-plugin/issues/34) و [#35](https://github.com/MohamedSalah-13/smart-report-plugin/issues/35).** للسبب نفسه صارت أرخص من قبل، وتطلبها الفواتير والكشوف مباشرة.
3. **javadoc jar بالإنجليزية ثم Maven Central، [#36](https://github.com/MohamedSalah-13/smart-report-plugin/issues/36).** يُزيل نقطة الفشل الواحدة في التوزيع، ويفتح المكتبة لمن لا يقرأ العامية.

ويُترك Jasper 7 والوضع الصارم الافتراضي لإصدار رئيسي واحد يجمعهما.

ومن الأفكار المضافة في 2026-11-28، الأولى بالبدء **إعداد الطباعة لكل نوع مستند ثم الطريق الذي يطبّقه، [#43](https://github.com/MohamedSalah-13/smart-report-plugin/issues/43) و [#44](https://github.com/MohamedSalah-13/smart-report-plugin/issues/44)**: هي الأكثر تكرارًا بين البرامج، ولا تعتمد على شيء. وترتيبها مع البنود الثلاثة أعلاه لم يُحسم.

## ما تغير منذ المراجعة السابقة

المراجعة السابقة (على 2.8.0، حتى commit `77c7d12`) وجدت بندًا مهمًا وخمسة تحسينات. الحالة الآن:

| البند في المراجعة السابقة | الحالة | الإصدار | PR |
|---|---|---|---|
| 🟠 قواعد تقسيم الصفحات لكل عنصر على حدة داخل `PageWriter` | ✅ عقد موحد للأجزاء (`Part` وقطعها)، وقواعد الصفحات كلها في `PageWriter`. المستندات تخرج كما كانت بالضبط: لقطات المواضع المسجلة على الكود القديم مرت كلها | 2.9.1 | [#30](https://github.com/MohamedSalah-13/smart-report-plugin/pull/30) |
| 🟡 الـ records موضعية والقاعدة غير مكتوبة | ✅ القاعدة مكتوبة في `RELEASING.md`؛ الـ records نفسها باقية (البند 4) | 2.9.1 | — |
| 🟡 عنوان Excel يبدأ أو ينتهي بفاصلة عليا يُسقط التقرير | ✅ اسم الورقة يُسقط الفواصل العليا من طرفيه، والعنوان في الخلية يبقى كما هو | 2.9.1 | [#29](https://github.com/MohamedSalah-13/smart-report-plugin/pull/29) |
| 🟡 Jasper مثبَّت على 6.x | ⏳ باقٍ (البند 1) | — | — |
| 🟡 الحرف الناقص `?` افتراضيًا | ⏳ باقٍ (البند 2) | — | — |
| 🟡 التوثيق والتوزيع | ⏳ باقٍ (البند 3) | — | — |

ومن أفكار التطوير:

| الفكرة | الحالة | الإصدار | PR |
|---|---|---|---|
| علامة مائية أو ختم («مسودة»، «صورة»، «ملغاة»، «مدفوع») | ✅ `PdfDocument.watermark`: فوق المحتوى كله بشفافية 15%، مائلة على الورق ومتكررة على الإيصال الحراري، ولا تعدّ محتوى فلا تفتح صفحة | 2.10.0 | [#31](https://github.com/MohamedSalah-13/smart-report-plugin/pull/31) |
| إجماليات فرعية في التقرير العام بالتجميع حسب عمود | ✅ `groupBy` و `subtotalLabel`: في PDF لا يبدأ الإجمالي الفرعي صفحة وحده، وللإجمالي الكلي خط سميك فوقه ومزدوج تحته؛ وفي Excel صيغ `SUBTOTAL` تتبع الفلتر | 2.10.0 | [#32](https://github.com/MohamedSalah-13/smart-report-plugin/pull/32) |

### من مراجعة 2.6.0

مراجعة 2.6.0 (commit `e997c69`) وجدت بندًا حرجًا وأربعة مهمة وأربعة تحسينات، وأُغلق أغلبها في 2.7.0 إلى 2.9.0:

| البند في مراجعة 2.6.0 | الحالة | الإصدار | PR |
|---|---|---|---|
| 🔴 العربي يختفي بصمت من PDF الخارج من Jasper | ✅ Noto Naskh كـ font extension افتراضي مع اختبارات عربية، ثم السطر الأول في الصندوق الأقصر قليلًا من سطره مع تحذير حين لا يُطبع نص | 2.7.0، 2.8.0 | [#20](https://github.com/MohamedSalah-13/smart-report-plugin/pull/20)، [#24](https://github.com/MohamedSalah-13/smart-report-plugin/pull/24) |
| 🟠 عنوان Excel يمطّ العمود الأول | ✅ العنوان خارج حساب العروض | 2.7.0 | [#18](https://github.com/MohamedSalah-13/smart-report-plugin/pull/18) |
| 🟠 تتبّع عرض الأعمدة يبطّئ Excel | ✅ العرض من عينة أول 500 صف | 2.7.0 | [#18](https://github.com/MohamedSalah-13/smart-report-plugin/pull/18) |
| 🟠 مسار الطباعة بلا اختبار و CI على Linux فقط | ✅ اختبار يطبع إلى طابعة وهمية، و Windows في مصفوفة CI | 2.7.0 | [#17](https://github.com/MohamedSalah-13/smart-report-plugin/pull/17) |
| 🟠 قواعد الصفحات داخل `PageWriter` | ✅ عقد الأجزاء | 2.9.1 | [#30](https://github.com/MohamedSalah-13/smart-report-plugin/pull/30) |
| 🟡 الخط إعداد عام والحرف الناقص `?` | ✅ في أغلبه: خط لكل مستند، و Liberation Sans للرموز، ووضع صارم. الباقي أن الوضع الصارم ليس الافتراضي (البند 2) | 2.9.0 | [#26](https://github.com/MohamedSalah-13/smart-report-plugin/pull/26) |
| 🟡 records موضعية ولا فحص توافق | ✅ فحص japicmp (2.7.0) والقاعدة المكتوبة (2.9.1)؛ الـ records باقية (البند 4) | 2.7.0، 2.9.1 | [#17](https://github.com/MohamedSalah-13/smart-report-plugin/pull/17) |
| 🟡 Jasper مثبَّت على 6.x | ⏳ باقٍ (البند 1) | — | — |
| 🟡 التوثيق والتوزيع | جزئي: Maven wrapper، و CI بأمر بناء JitPack، وفحص JitPack بعد كل وسم، و `RELEASING.md`. javadoc jar و Maven Central باقيان (البند 3) | 2.7.1، 2.8.0 | [#21](https://github.com/MohamedSalah-13/smart-report-plugin/pull/21)، [#22](https://github.com/MohamedSalah-13/smart-report-plugin/pull/22) |

وصدر من أفكار تلك المراجعة: صف الإجمالي في التقرير العام (2.8.0، [#23](https://github.com/MohamedSalah-13/smart-report-plugin/pull/23))، وتثبيت صف الرأس و autofilter في Excel، والطباعة المباشرة `printOrShow`، و QR المرحلة الثانية من ZATCA، والتفقيط الإنجليزي والعملات الخليجية (2.7.0)، و `pageBreak()` و `PdfBatch` والخط لكل مستند (2.9.0، [#26](https://github.com/MohamedSalah-13/smart-report-plugin/pull/26) و [#27](https://github.com/MohamedSalah-13/smart-report-plugin/pull/27)).

## ما لم يُفحص

الفحوص اليدوية الباقية مجموعة في [#42](https://github.com/MohamedSalah-13/smart-report-plugin/issues/42).

- لم تُجرَّب الطباعة على طابعة حقيقية (A4 أو حرارية)، ولا المكتبة على module path ولا على Linux بلا خطوط نظام، ولم يُجرَّب Jasper بقوالب برامج حقيقية.
- الكشف المجمّع في Excel قُرئ بـ POI ولم يُفتح في Excel نفسه في هذه المراجعة: سلوك الفلتر مع الإجماليات الفرعية مأخوذ من جلسة #32 ومن التوثيق ([README.md:640](../README.md#L640)). والفرز في ورقة مجمّعة يخلط الإجماليات الفرعية بالبيانات، وهذا قيد موثَّق لا عيب.
- أن قارئ PDF يستبعد العلامة المائية من النسخ والبحث يعتمد على القارئ (هي معلَّمة artifact)؛ لم يُجرَّب في Acrobat ولا في المتصفحات.
- هذا التحديث لم يُعِد قراءة الكود كله: نقاط القوة وبنود 🟡 الباقية منقولة من المراجعة السابقة بعد التحقق من أن أرقام أسطرها ما زالت تشير إلى الكود الصحيح. ولم تُراجع `README.ar.md` و `SKILL.md` سطرًا سطرًا.
