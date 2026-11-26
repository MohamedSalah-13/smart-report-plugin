<div dir="rtl">

# Smart Report Plugin

![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white) ![JavaFX](https://img.shields.io/badge/JavaFX-21-2E7DAF?logo=java&logoColor=white) ![Apache PDFBox](https://img.shields.io/badge/Apache%20PDFBox-3.0.3-D22128?logo=apache&logoColor=white) ![Apache POI](https://img.shields.io/badge/Apache%20POI-5.3.0-D22128?logo=apache&logoColor=white) ![JasperReports](https://img.shields.io/badge/JasperReports-6.21.3-C2185B) ![Release](https://img.shields.io/github/v/tag/MohamedSalah-13/smart-report-plugin) ![License](https://img.shields.io/github/license/MohamedSalah-13/smart-report-plugin)

[English](README.md) | **العربية**

مكتبة جافا للتقارير والطباعة، بتضاف كـ dependency لأي مشروع:

- **مستندات PDF عربية جاهزة للطباعة** على A4 و A5 (طولي وعرضي): ترويسة، عنوان، بيانات، أكتر من جدول بصف إجمالي، ملخص، ترقيم صفحات — وإرسال مباشر للطابعة. عبر Apache PDFBox فقط.
- **نافذة معاينة JavaFX** جاهزة: الصفحات كما هتتطبع، اختيار الطابعة والمقاس وعدد النسخ، طباعة وحفظ PDF.
- **PDF أو Excel عام من `List<POJO>`** بدون أي قالب.
- **JasperReports** (قوالب `.jrxml`/`.jasper` مصممة بصريًا): تصدير PDF, XLSX, HTML, أو CSV.

<p align="center"><img src="docs/statement-a5.png" alt="كشف حساب على ورقة A5" width="420"></p>

## ما الجديد

### 2.1.1

- **إصلاح:** صف الجدول الأطول من صفحة كاملة (خلية نصها بيلتف على سطور أكتر من اللي الصفحة تشيله) ما بقاش يترسم تحت حافة الورقة ويضيع باقيه في الطباعة؛ بقى يكمل على الصفحة اللي بعدها تحت رأس الجدول.

### 2.1.0

- **جديد:** نافذة معاينة JavaFX جاهزة (وحدة `smart-report-fx`): الصفحات كما هتتطبع، اختيار الطابعة والمقاس وعدد النسخ، طباعة وحفظ PDF.
- **جديد:** skill لـ Claude Code يربط المكتبة بأي مشروع (`claude-skill/pdf-print`).

تفاصيل كل إصدار في [CHANGELOG.md](CHANGELOG.md).

## الوحدات

كل نوع في وحدة Maven لوحده، فالبرنامج اللي بيطبع PDF بس ما يشيلش POI ولا Jasper:

| الوحدة | فيها إيه | بتجيب معاها |
|---|---|---|
| `smart-report-pdf` | `PdfDocument`، `PdfPrinter`، `PdfPages`، وتقرير PDF العام | PDFBox |
| `smart-report-fx` | `PdfPreview`: نافذة المعاينة | `smart-report-pdf`؛ و JavaFX بتاع برنامجك (`provided`) |
| `smart-report-excel` | تقرير Excel العام | Apache POI |
| `smart-report-jasper` | تقارير قوالب Jasper | JasperReports |
| `smart-report-api` | `ReportService` و`ReportRequest` — بتيجي لوحدها مع أي وحدة فوق | slf4j-api فقط |

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
    <version>v2.1.1</version>
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
        .footer("شكرًا لتعاملكم");

document.save(Path.of("statement.pdf"));     // ملف
byte[] pdf = document.toBytes();             // أو بايتات
```

</div>

- `PdfDocument.rtl(...)` للعربي و`PdfDocument.ltr(...)` للإنجليزي. الاتجاه بيحدد مكان أول عمود ومحاذاة النص.
- الأعمدة والخلايا بتتكتب **بالترتيب المنطقي**؛ المكتبة هي اللي بتحط أول عمود على اليمين في الصفحة العربية.
- رقم العمود (22، 14، 26) وزنه من عرض الجدول، ومفيش عمود بينزل عن عرض أطول كلمة فيه.
- النص الأطول من عموده **بيلتف** على أكتر من سطر، ورأس الجدول بيتكرر في كل صفحة، والصفحات بتترقّم ("صفحة 2 من 3") لما المستند يبقى أكتر من صفحة.
- الصف الأطول من صفحة كاملة بيكمل على الصفحة اللي بعدها.
- الأرقام السالبة، التواريخ، النسب، وأرقام التليفون بتتقرا زي ما اتكتبت جوّه الجملة العربية.

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

> العربي بيتظبط تلقائيًا قبل الرسم: الحروف بتتوصّل ببعضها وبتترتب من اليمين لليسار، مع دمج لام-ألف.
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

تقرير الـ PDF العام بيبقى من اليمين لليسار لو العنوان أو أي رأس عمود فيه عربي، على A4 طولي. للتغيير:

<div dir="ltr">

```java
.parameter(PdfReportProvider.PAPER, "A5-landscape")        // أو كائن Paper
.parameter(PdfReportProvider.RIGHT_TO_LEFT, true)
```

</div>

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
