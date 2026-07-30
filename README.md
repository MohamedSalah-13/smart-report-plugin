# Smart Report Plugin

مكتبة (plugin) بجافا بتضاف كـ dependency لأي مشروع، وبتوفر واجهة موحّدة لطباعة كل أنواع التقارير من `List<POJO>`:

- **PDF عام** (بدون قالب): جدول (عنوان + رأس أعمدة + صفوف بيانات) مع ترقيم صفحات تلقائي — عبر Apache PDFBox.
- **Excel عام** (`.xlsx`, بدون قالب): نفس الفكرة، برأس منسّق وأعمدة بعرض تلقائي — عبر Apache POI.
- **JasperReports** (قوالب `.jrxml`/`.jasper` مصممة بصريًا): تصدير PDF, XLSX, HTML, أو CSV من نفس القالب.

مبني بـ Java 21 و Maven.

## المتطلبات

- Java 21+
- Maven 3.6+

## التثبيت

المشروع مش منشور على Maven Central، فبتضيفه محليًا:

```bash
mvn install
```

وبعدين تضيفه كـ dependency في `pom.xml` بتاع مشروعك:

```xml
<dependency>
    <groupId>com.smart.report</groupId>
    <artifactId>smart-report-plugin</artifactId>
    <version>1.0.0</version>
</dependency>
```

> المكتبة بتستخدم `slf4j-api` فقط (بدون implementation)، فلو عايز تشوف الـ logs، ضيف backend من اختيارك (logback, slf4j-simple, ...) في مشروعك.

## الاستخدام السريع

### PDF أو Excel عام (بدون قالب)

نفس الـ `ReportRequest` يشتغل مع النوعين، بس تغيّر `ReportType`:

```java
import com.smart.report.*;

List<Employee> employees = employeeRepository.findAll();

ReportRequest<Employee> request = ReportRequest.<Employee>builder()
        .title("تقرير الموظفين")
        .data(employees)
        .addColumn("id", "الرقم")
        .addColumn("name", "الاسم")
        .addColumn("department", "القسم")
        .addColumn("salary", "الراتب")
        .build();

byte[] pdf = ReportService.generate(ReportType.PDF, request);
byte[] xlsx = ReportService.generate(ReportType.EXCEL, request);

// أو تكتب مباشرة لملف
ReportService.generateToFile(ReportType.PDF, request, Path.of("employees.pdf"));
```

اسم كل عمود (`addColumn("id", ...)`) بيتقرا من الكائن عن طريق reflection: بيدوّر أول حاجة موجودة من `getId()`، `isId()`، أو `id()` (مكوّنات الـ record)، وأخيرًا الحقل مباشرة.

### JasperReports (قالب مصمم بصريًا)

```java
import com.smart.report.jasper.JasperExportFormat;

ReportRequest<Employee> request = ReportRequest.<Employee>builder()
        .data(employees)
        .template("reports/employee.jrxml") // ملف على القرص أو مورد على الـ classpath
        .parameter("ReportTitle", "تقرير الموظفين")
        .exportFormat(JasperExportFormat.PDF) // أو XLSX / HTML / CSV
        .build();

byte[] pdf = ReportService.generate(ReportType.JASPER, request);
```

`request.data()` بيتحوّل تلقائيًا لـ `JRBeanCollectionDataSource` عشان تستخدمه كـ `$F{...}` جوه القالب، و`request.parameters()` بتتبعت كـ report parameters (`$P{...}`).

## أنواع التقارير (`ReportType`)

| القيمة | الوصف | يحتاج قالب؟ |
|---|---|---|
| `PDF` | PDF جدولي عام (PDFBox) | لأ |
| `EXCEL` | ملف `.xlsx` عام (Apache POI) | لأ |
| `JASPER` | تصدير من قالب JasperReports | أيوه (`.jrxml`/`.jasper`) |

## صيغ تصدير Jasper (`JasperExportFormat`)

| القيمة | الوصف |
|---|---|
| `PDF` | ملف PDF |
| `XLSX` | جدول بيانات Excel |
| `HTML` | صفحة HTML |
| `CSV` | ملف CSV |

## الاختبارات

```bash
mvn test
```

الاختبارات بتولّد تقارير حقيقية (PDF عام، Excel عام، Jasper→PDF، Jasper→XLSX) وتفتح الملف الناتج فعليًا للتحقق من محتواه (نصوص PDF عبر `PDFTextStripper`، خلايا Excel عبر POI).

## البناء

```bash
mvn package
```

بينتج `target/smart-report-plugin-1.0.0.jar`.

## الرخصة

المشروع مرخّص تحت [MIT License](LICENSE).
