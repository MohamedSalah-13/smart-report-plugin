<div dir="rtl">

# Smart Report Plugin

![CI](https://github.com/MohamedSalah-13/smart-report-plugin/actions/workflows/ci.yml/badge.svg) ![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white) ![JavaFX](https://img.shields.io/badge/JavaFX-21-2E7DAF?logo=java&logoColor=white) ![Apache PDFBox](https://img.shields.io/badge/Apache%20PDFBox-3.0.8-D22128?logo=apache&logoColor=white) ![Apache POI](https://img.shields.io/badge/Apache%20POI-6.0.0-D22128?logo=apache&logoColor=white) ![JasperReports](https://img.shields.io/badge/JasperReports-6.21.5-C2185B) ![Release](https://img.shields.io/github/v/tag/MohamedSalah-13/smart-report-plugin) ![License](https://img.shields.io/github/license/MohamedSalah-13/smart-report-plugin)

[English](README.md) | **العربية**

مكتبة جافا للتقارير والطباعة، بتضاف كـ dependency لأي مشروع:

- **مستندات PDF عربية جاهزة للطباعة** على A4 و A5 (طولي وعرضي): ترويسة، عنوان، بيانات، أكتر من جدول بصف إجمالي، ملخص، ترقيم صفحات، رمز QR، باركود — وإرسال مباشر للطابعة. عبر Apache PDFBox (و ZXing core لتشفير رمز QR والباركود).
- **نافذة معاينة JavaFX** جاهزة: الصفحات كما هتتطبع، اختيار الطابعة والمقاس وعدد النسخ، طباعة وحفظ PDF.
- **PDF أو Excel عام من `List<POJO>`** بدون أي قالب.
- **JasperReports** (قوالب `.jrxml`/`.jasper` مصممة بصريًا): تصدير PDF, XLSX, HTML, أو CSV.

<p align="center"><img src="docs/statement-a5.png" alt="كشف حساب على ورقة A5" width="420"></p>

## ما الجديد

### 2.6.0

- **جديد:** باركود Code 128 لرقم شحنة أو بوليصة أو فاتورة أو صنف يقراه الماسح اليدوي. خطوط متجهة وتحتها الكود مكتوب، وبيكفي في لفة 58 مم. التفاصيل في [باركود](#باركود-code-128).
- **إصلاح:** خانات التوقيع ما بقتش تنزل لوحدها في صفحة جديدة: لو ما كفّاهاش المكان تحت الملخص، الملخص (والتفقيط اللي تحته) بيتنقل معاها.

### 2.5.0

- **جديد:** رمز QR على الصفحة - رابط، أو رقم فاتورة، أو رمز الفاتورة الضريبية المبسطة في السعودية (`ZatcaQr`، المرحلة الأولى). مربعات متجهة حادة، وبيكفي في لفة 58 مم. التفاصيل في [رمز QR](#رمز-qr-ورمز-الفاتورة-الضريبية-في-السعودية).
- **إصلاح:** المعاينة بتفتح الإيصال الطويل بعرض يتقري، ومن غير صورة ضخمة.
- **ملاحظة ترقية:** وحدة الـ Excel بقت بتجيب Apache POI 6.0.0 (بدل 5.3.0).

تفاصيل كل إصدار في [CHANGELOG.md](CHANGELOG.md).

## الوحدات

كل نوع في وحدة Maven لوحده، فالبرنامج اللي بيطبع PDF بس ما يشيلش POI ولا Jasper:

| الوحدة | فيها إيه | بتجيب معاها |
|---|---|---|
| `smart-report-pdf` | `PdfDocument`، `PdfPrinter`، `PdfPages`، وتقرير PDF العام | PDFBox، و ZXing core (رمز QR والباركود) |
| `smart-report-fx` | `PdfPreview`: نافذة المعاينة | `smart-report-pdf`؛ و JavaFX بتاع برنامجك (`provided`) |
| `smart-report-excel` | تقرير Excel العام | Apache POI |
| `smart-report-jasper` | تقارير قوالب Jasper | JasperReports |
| `smart-report-api` | `ReportService` و`ReportRequest` و`AmountInWords` و`ZatcaQr` — بتيجي لوحدها مع أي وحدة فوق | slf4j-api فقط |

## التثبيت

المكتبة بتتسحب من [JitPack](https://jitpack.io) مباشرة من tags المستودع:

<div dir="ltr">

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.MohamedSalah-13.smart-report-plugin</groupId>
    <artifactId>smart-report-pdf</artifactId>
    <version>v2.6.0</version>
</dependency>
```

</div>

غيّر `artifactId` لـ `smart-report-fx` أو `smart-report-excel` أو `smart-report-jasper` حسب اللي محتاجه.
مبنية بـ Java 21. وحدة `smart-report-fx` موجودة من `v2.1.0`.

> المكتبة بتستخدم `slf4j-api` فقط (بدون implementation)، فلو عايز تشوف الـ logs ضيف backend من اختيارك
> (logback, slf4j-simple, ...) في مشروعك.

## مستند PDF (`PdfDocument`)

المستند بيتبني جزءًا بعد جزء بالترتيب اللي هيظهر بيه:

<div dir="ltr">

```java
import com.smart.report.pdf.*;

PdfDocument document = PdfDocument.rtl(Paper.A5)            // أو Paper.A4، أو Paper.A4.landscape()
        .letterhead("شركة الصفا لنقل مواد البناء", "القاهرة - ت: 0100 000 0000")
        .title("كشف حساب عربية")
        .subtitle("الفترة: من 2026-11-01 إلى 2026-11-30")
        .field("رقم العربية", "ق ط ع 1234").field("السائق", "أحمد محمد علي")
        .table(t -> t
                .column("التاريخ", 22).column("العدد", 14, Align.CENTER).column("القيمة", 26, Align.CENTER)
                .rows(movements, m -> new String[]{date(m), trips(m), amount(m)})
                .totals("الإجمالى", "12 نقلة", "-7,200.00"))
        .heading("الواصل والتسويات")
        .table(payments)
        .summary("المستحق عن الفترة", "7,200.00")
        .summary("الواصل", "4,000.00")
        .summaryTotal("الباقى", "3,200.00")
        .footer("شكرًا لتعاملكم")
        .signatures("المستلم", "المحاسب", "الختم");          // خانات توقيع، بتفضل مع الملخص اللي فوقها

document.save(Path.of("statement.pdf"));     // ملف
byte[] pdf = document.toBytes();             // أو بايتات
```

</div>

- `PdfDocument.rtl(...)` للعربي و`PdfDocument.ltr(...)` للإنجليزي. الاتجاه بيحدد مكان أول عمود ومحاذاة النص.
- الأعمدة والخلايا بتتكتب **بالترتيب المنطقي**؛ المكتبة هي اللي بتحط أول عمود على اليمين في الصفحة العربية.
- رقم العمود (22، 14، 26) وزنه من عرض الجدول، ومفيش عمود بينزل عن عرض أطول كلمة فيه.
- النص الأطول من عموده **بيلتف** على أكتر من سطر، ورأس الجدول بيتكرر في كل صفحة، والصفحات بتترقّم ("صفحة 2 من 3") لما المستند يبقى أكتر من صفحة.
- الصف الأطول من صفحة كاملة بيكمل على الصفحة اللي بعدها، وكذلك البيان اللي قيمته أطول من صفحة. ورأس الجدول
  الأطول من تلت الصفحة بيترسم مرة واحدة بدل ما يتكرر في كل صفحة.
- الأرقام السالبة، التواريخ، النسب، وأرقام التليفون بتتقرا زي ما اتكتبت جوّه الجملة العربية.

### التفقيط (`AmountInWords`)

الإجمالي مكتوبًا بالحروف، زي ما بيتكتب في الفاتورة أو الإيصال:

<div dir="ltr">

```java
import com.smart.report.util.AmountInWords;

document.text(AmountInWords.arabic(new BigDecimal("7200"), AmountInWords.Currency.EGP));
// فقط سبعة آلاف ومائتا جنيه لا غير

AmountInWords.arabic(new BigDecimal("1234.50"), AmountInWords.Currency.EGP);
// فقط ألف ومائتان وأربعة وثلاثون جنيهًا وخمسون قرشًا لا غير
AmountInWords.arabicWords(new BigDecimal("0.13"), AmountInWords.Currency.SAR);   // من غير «فقط ... لا غير»
// ثلاث عشرة هللةً
```

</div>

- المعدود بيتبع العدد: `جنيه واحد`، `جنيهان`، `ثلاثة جنيهات`، `أحد عشر جنيهًا`، `مائة جنيه`، `مائتا جنيه`، `ألفا جنيه`.
- العدد بيوافق جنس المعدود: `ثلاثة جنيهات` لكن `ثلاث هللات`.
- عملات جاهزة: `EGP` و`SAR` و`AED` و`KWD` (3 خانات للفلس) و`USD`، وأي عملة تانية `new Currency(...)` بصيغ اسمها.
- المبلغ بيتقرّب لخانات العملة (النص لفوق)، والسالب أو اللي من ألف مليار وأكتر بيترفض.
- موجود في `smart-report-api`، فتقارير Jasper و Excel تقدر تستخدمه كمان (كـ parameter مثلًا).

### في كل صفحة

<div dir="ltr">

```java
document.letterheadOnEveryPage(true)                              // الترويسة (والشعار) أعلى كل صفحة
        .pageFooter("صادر من نظام نقلات - ت: 0100 000 0000");      // سطر صغير أسفل كل صفحة
```

</div>

- `letterheadOnEveryPage(true)`: للكشوف الطويلة؛ والجدول اللي بيكمل على صفحة جديدة رأسه بييجي تحت الترويسة.
- `pageFooter(text)`: فوق رقم الصفحة في كل صفحة، حتى لو المستند صفحة واحدة. غير `footer(text)` اللي هو جملة
  ختامية بتترسم مرة واحدة بعد آخر جزء.
- `signatures(...)`: الخانات بتفضل مع بعض، ومع الملخص اللي قبلها (وأي نص بينهم زي التفقيط): لو ما كفّاهاش
  المكان تحته، الملخص بيتنقل معاها للصفحة الجاية بدل ما تنزل هي لوحدها. ولو المجموعة دي ما ينفعش تتنقل،
  آخر سطر في النص اللي قبل الخانات بينزل معاها على الأقل.

### إيصالات الطابعة الحرارية (80 مم و 58 مم)

<div dir="ltr">

```java
PdfDocument receipt = PdfDocument.rtl(Paper.ROLL_80)          // أو Paper.ROLL_58
        .letterhead("سوبر ماركت الهدى", "ت: 0100 000 0000")
        .table(items)
        .summaryTotal("الإجمالى", "85.00")
        .footer("شكرًا لزيارتكم");
```

</div>

- الإيصال **صفحة واحدة بطول المكتوب فيه**، مش ورقة A4: اللفة بتتقص عند آخر سطر.
- الخط أصغر والهوامش ضيقة على اللفة. و`PdfPrinter` ما بيطلبش من الطابعة مقاس ورق، فبتستخدم لفتها.
- `Paper.parse("80mm")` ونافذة المعاينة بيعرفوا اللفات كمان. المعاينة بتفتح الإيصال بمقاس يتقري (بعرض النافذة
  لحد ضعف مقاسه) بدل ما تحشر طوله كله في النافذة.

### رمز QR (ورمز الفاتورة الضريبية في السعودية)

<div dir="ltr">

```java
document.qrCode("https://example.com/invoices/2026-0042");   // 30 مم، في المنتصف
document.qrCode(invoiceNumber, 25, Align.START);              // 25 مم، عند بداية السطر (يمين في العربي)
```

</div>

رمز QR اللي بتشيله الفاتورة الضريبية المبسطة في السعودية (الفوترة الإلكترونية، المرحلة الأولى — هيئة الزكاة
والضريبة والجمارك): اسم البائع، والرقم الضريبي، والوقت، والإجمالي شامل الضريبة، والضريبة، بالترميز اللي الهيئة
طالباه:

<div dir="ltr">

```java
import com.smart.report.util.ZatcaQr;

String qr = ZatcaQr.payload("مؤسسة الهدى للتجارة", "310122393500003",
        LocalDateTime.now().atZone(ZoneId.of("Asia/Riyadh")).toInstant(),
        new BigDecimal("1150.00"), new BigDecimal("150.00"));

PdfDocument receipt = PdfDocument.rtl(Paper.ROLL_80)
        .letterhead("مؤسسة الهدى للتجارة", "الرقم الضريبي 310122393500003")
        .title("فاتورة ضريبية مبسطة")
        .table(items)
        .summary("ضريبة القيمة المضافة 15%", "150.00")
        .summaryTotal("الإجمالى شامل الضريبة", "1,150.00")
        .qrCode(qr);
```

</div>

- مرسوم مربعات متجهة مش صورة، فبيفضل حادًا على أي طابعة. حواليه منطقة فاضية بعرض 4 مربعات، وما بيتقسمش على
  صفحتين أبدًا.
- المقاس هو ضلع الرمز بالمليمتر. على اللفة الضيقة الرمز الأعرض من السطر بيصغر لحد ما يكفي، والإيصال بيتقص
  تحته على طول.
- `Align.START` عند بداية السطر (يمين في المستند العربي)، و`Align.END` عند آخره، و`Align.CENTER` (الافتراضي) في
  المنتصف. والنص العربي بيتشفّر UTF-8.
- `ZatcaQr.payload` بيرجّع Base64 لقيم الـ TLV (الأرقام من 1 لـ 5، وطول كل قيمة بعدد بايتاتها في UTF-8، فالاسم
  العربي بيتعد ببايتاته مش بحروفه). الوقت بيتكتب بتوقيت UTC والمبالغ بخانتين عشريتين، والرقم الضريبي اللي مش
  15 رقمًا أوله وآخره 3 بيترفض. موجود في `smart-report-api` ومش محتاج أي مكتبة.
- رمز المرحلة الثانية (الربط) فيه قيم زيادة (بصمة الفاتورة وتوقيعها) ما بتطلعش غير من توقيع الفاتورة نفسها؛
  `ZatcaQr` للمرحلة الأولى.
- الرمز بيتشفّر بـ [ZXing](https://github.com/zxing/zxing) core (رخصة Apache 2.0، ومالهاش dependencies)،
  و`smart-report-pdf` بيجيبها معاه.

### باركود (Code 128)

لرقم الشحنة أو البوليصة أو الفاتورة أو كود الصنف، بيتقري بقارئ اليد:

<div dir="ltr">

```java
document.barcode("SHP-2026-0042");                    // خطوط بطول 12 مم، في المنتصف، والكود مكتوب تحتها
document.barcode(waybillNumber, 15, Align.START);     // 15 مم، عند بداية السطر (يمين في العربي)
```

</div>

- مرسوم خطوط متجهة مش صورة، وتحته الكود مكتوب من الشمال لليمين زي ما هو حتى في الصفحة العربية. حواليه من
  الجنبين منطقة فاضية بعرض 10 خطوط، والخطوط والكود ما بيتقسموش على صفحتين أبدًا.
- أضيق خط 0.33 مم (حوالي 3 نقط على الطابعة الحرارية 203 نقطة في البوصة). الكود الأعرض من السطر خطوطه بتضيق
  لحد 0.25 مم؛ واللي يحتاج أضيق من كده بيفشل بـ `ReportException` فيها الكود، بدل باركود ما بيتقريش. على لفة
  58 مم ده حوالي 15 حرف؛ الأرقام المتتالية بتتضغط اتنين في رمز، فالكود اللي أغلبه أرقام بيطلع أقصر.
- Code 128 بيشيل الإنجليزي والأرقام والعلامات بس: النص العربي أو الحروف بعلامات أو الـ tab بيترفضوا بـ
  `IllegalArgumentException` وقت إضافة الباركود.

### الشكل (`PdfStyle`)

من غير تحديد، الشكل بيتاخد حسب الورقة (`PdfStyle.forPaper`): خط وهوامش أصغر على A5. للتعديل:

<div dir="ltr">

```java
document.style(PdfStyle.forPaper(Paper.A4)
        .withHeaderColors(0x2C3E50, 0xFFFFFF)   // رأس جدول غامق بخط أبيض
        .withStripes(true)                      // تلوين الصفوف المتبادلة
        .withMargin(28f));

document.style(PdfStyle.forPaper(Paper.A5).inkSaver());   // بلا أي تعبئة: خطوط بدل الخلفيات
```

</div>

### الطباعة (`PdfPrinter`)

<div dir="ltr">

```java
List<String> printers = PdfPrinter.printerNames();          // الطابعات المعرّفة على الجهاز
Optional<String> preferred = PdfPrinter.defaultPrinter();

PdfPrinter.print(document, printerName, 1);                 // المستند على ورقته، نسخة واحدة
PdfPrinter.print(pdfBytes, printerName, Paper.A5, 2);       // أو PDF جاهز
```

</div>

الطابعة بتتطلب منها ورقة المستند (A4 أو A5) والصفحات بتتبعت بحجمها الفعلي. `printerName` الفاضي معناه
الطابعة الافتراضية. الطباعة بتاخد وقتها، فنادِها من thread غير thread الواجهة. لو الطابعة مش على
الجهاز بترمي `ReportException` باسمها وبالطابعات المتاحة.

### المعاينة (`PdfPreview`)

وحدة `smart-report-fx` فيها نافذة معاينة لأي برنامج JavaFX:

<p align="center"><img src="docs/preview-window.png" alt="نافذة معاينة الطباعة" width="560"></p>

<div dir="ltr">

```java
import com.smart.report.fx.PdfPreview;

PdfPreview.of(paper -> StatementPdf.document(statement, companyName, paper))
        .papers(Paper.A4, Paper.A5)       // مقاس واحد = بلا اختيار مقاس في النافذة
        .title("كشف حساب عربية 1751")
        .fileName("كشف 1751")              // الاسم المقترح عند «حفظ PDF»
        .rightToLeft(true)                 // نافذة عربية؛ false = إنجليزية
        .remember("statement")             // آخر طابعة ومقاس، لكل نوع مستند
        .stylesheet(appStylesheetUrl)      // اختياري: CSS بتاع برنامجك
        .show(ownerWindow);

PdfPreview.of(document).show(ownerWindow);   // أو مستند جاهز على ورقته
```

</div>

- النافذة بتعرض الصفحات بنفس الرسم اللي الطابعة بتستلمه، وما بتحجزش البرنامج.
- لما المستخدم يغيّر المقاس، المستند بيتبني من جديد على المقاس الجديد.
- الدالة اللي بتبني المستند بتتنادى **خارج thread الواجهة**: اقرا بياناتك قبلها وما تلمسش فيها عناصر JavaFX.
- كلام النافذة عربي أو إنجليزي حسب `rightToLeft`، و`texts(PreviewTexts)` يبدّله بمصطلحات برنامجك.
- لرسم الصفحات كصور بنفسك (معاينة مخصّصة، صورة مصغّرة): `PdfPages.open(pdf).render(pageIndex, scale)` في `smart-report-pdf`.

### الخط

المكتبة فيها خط **Noto Naskh Arabic** مضمَّن (عادي وعريض)، فالناتج واحد على كل جهاز من غير أي إعداد.
لتغييره، بالترتيب ده:

1. الخاصية `smart.report.pdf.font` (واختياريًا `smart.report.pdf.font.bold`) — مسار ملف `.ttf`.
2. مورد على الـ classpath اسمه `fonts/report-font.ttf` (و`fonts/report-font-bold.ttf`).

الحرف اللي مش في الخط المختار بيتجرّب في خط النظام (Arial وغيره)، وآخر حل بيتطبع `?` مع تحذير في الـ log.

> العربي بيتظبط تلقائيًا قبل الرسم: الحروف بتتوصّل ببعضها وبتترتب من اليمين لليسار، مع دمج لام-ألف،
> ومعاها حروف زي ڤ و چ و گ و پ و ک و ی.
> ده شغل `ArabicTextShaper`، وما بيحتاجش أي dependency إضافية (لا ICU4J ولا غيره).
> الحركات (التشكيل) بتتوسّط فوق حروفها تقريبيًا — PDFBox ما بيطبّقش جداول GPOS في الخط.

## PDF أو Excel عام (بدون قالب)

نفس الـ `ReportRequest` يشتغل مع النوعين، بس تغيّر `ReportType`:

<div dir="ltr">

```java
import com.smart.report.*;

ReportRequest<Employee> request = ReportRequest.<Employee>builder()
        .title("تقرير الموظفين")
        .data(employees)
        .addColumn("id", "الرقم")
        .addColumn("name", "الاسم")
        .addColumn("salary", "الراتب")
        .build();

byte[] pdf = ReportService.generate(ReportType.PDF, request);     // محتاج smart-report-pdf
byte[] xlsx = ReportService.generate(ReportType.EXCEL, request);  // محتاج smart-report-excel

ReportService.generateToFile(ReportType.PDF, request, Path.of("employees.pdf"));
```

</div>

اسم كل عمود (`addColumn("id", ...)`) بيتقرا من الكائن عن طريق reflection: `getId()`، `isId()`، `id()`
(مكوّنات الـ record)، وأخيرًا الحقل مباشرة.

العمود ممكن ياخد خياراته في تقرير الـ PDF — نمط تنسيق، محاذاة، ووزن من عرض الجدول:

<div dir="ltr">

```java
.addColumn(ColumnDefinition.of("amount", "المبلغ").withFormat("#,##0.00").withAlign(Alignment.END).withWeight(2))
.addColumn(ColumnDefinition.of("date", "التاريخ").withFormat("d MMMM yyyy"))   // 26 نوفمبر 2026
```

</div>

- `withFormat`: نمط `DecimalFormat` للأرقام (التقريب لفوق من النص) أو نمط `DateTimeFormatter` للتواريخ. أسماء
  الشهور والأيام بلغة التقرير والأرقام لاتينية. النمط اللي ما ينفعش مع قيمة (`HH:mm` على تاريخ من غير وقت)
  بيرمي `ReportException` باسم العمود.
- `withAlign`: `START` أو `CENTER` أو `END`، منسوبة لاتجاه التقرير. من غيرها، عمود الأرقام لنهاية السطر وأي
  عمود تاني لبدايته.
- `withWeight`: نصيب العمود من عرض الجدول (2 = ضعف العمود العادي).

تقرير الـ Excel بياخد النمط (محوَّلًا لصيغة Excel، فالرقم بيفضل رقم والتاريخ تاريخ) والمحاذاة، وبيتجاهل الوزن:
عرض أعمدته على قد محتواها. حرف نمط تاريخ مالوش مقابل في Excel (المنطقة الزمنية `z`)، أو ساعة Excel هيعرضها بشكل تاني (`hh` من غير `a`)،
بيرمي `ReportException` باسم العمود.

التقريرين بيبقوا من اليمين لليسار لو العنوان أو أي رأس عمود فيه عربي (في Excel الورقة نفسها بتبقى من اليمين).
تقرير الـ PDF على A4 طولي. للتغيير:

<div dir="ltr">

```java
.parameter(PdfReportProvider.PAPER, "A5-landscape")        // أو كائن Paper
.parameter(PdfReportProvider.RIGHT_TO_LEFT, true)
.parameter(ExcelReportProvider.RIGHT_TO_LEFT, true)       // اتجاه ورقة الـ Excel
```

</div>

في الـ PDF، الأرقام بفواصل الآلاف (`1,234,567.5`، `1,200.50`) والتواريخ بصيغة `2026-11-26` و`2026-11-26 10:30:00`،
والعمود اللي قيمه كلها أرقام بيتحاذى لنهاية السطر فالأرقام تيجي تحت بعض.

في Excel، الأرقام والمنطقيات والتواريخ بتتكتب بأنواعها الأصلية مش كنصوص.

لو طلبت نوعًا وحدته مش على الـ classpath، `ReportException` بتقول لك تضيف أنهي dependency.

## JasperReports (قالب مصمم بصريًا)

<div dir="ltr">

```java
import com.smart.report.JasperExportFormat;

ReportRequest<Employee> request = ReportRequest.<Employee>builder()
        .data(employees)
        .template("reports/employee.jrxml") // ملف على القرص أو مورد على الـ classpath
        .parameter("ReportTitle", "تقرير الموظفين")
        .exportFormat(JasperExportFormat.PDF) // أو XLSX / HTML / CSV
        .build();

byte[] pdf = ReportService.generate(ReportType.JASPER, request);  // محتاج smart-report-jasper
```

</div>

`request.data()` بيتحوّل تلقائيًا لـ `JRBeanCollectionDataSource`، و`request.parameters()` بتتبعت كـ
report parameters (`$P{...}`). القوالب المصرَّفة بتتخزّن ويُعاد استخدامها.

## مع Claude Code

المجلد `claude-skill/pdf-print` فيه skill يربط المكتبة بأي مشروع حالي أو جديد: الاعتماد، بناء المستند من
بيانات البرنامج، نافذة المعاينة، والاختبار. انسخه إلى `~/.claude/skills/` ثم قل لـ Claude: «ضيف طباعة PDF
للفاتورة» أو «اعمل معاينة قبل الطباعة».

## الترقية من 1.0

- المكتبة بقت وحدات: بدّل `smart-report-plugin` بالوحدة اللي بتستخدمها (`smart-report-pdf` / `-excel` / `-jasper`).
- `JasperExportFormat` اتنقل من `com.smart.report.jasper` لـ `com.smart.report`.
- لو كاتب `ReportProvider` خاصًا بيك: زاد عليه `type()`، وبيتسجّل في `META-INF/services/com.smart.report.ReportProvider`.
- تقرير الـ PDF العام بقى يلتف النص الطويل بدل تقطيعه بـ `...`، والتقرير العربي جدوله من اليمين.

## الاختبارات والبناء

<div dir="ltr">

```bash
mvn test       # كل الوحدات
mvn install    # يبني الوحدات وينزّلها في المستودع المحلي
```

</div>

اختبارات الـ PDF بتقرا **مواضع** الحروف المرسومة مش النص المستخرج بس: عيوب الاتجاه (عمود في الناحية
الغلط، إجمالي تحت عمود تاني، آخر الجملة في السطر الأول) ما بتظهرش في النص.

## الرخص

المشروع مرخّص تحت [MIT License](LICENSE). الخط المضمَّن Noto Naskh Arabic مرخّص تحت
[SIL Open Font License 1.1](smart-report-pdf/src/main/resources/com/smart/report/pdf/fonts/OFL.txt).

</div>
