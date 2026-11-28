package com.smart.report.pdf;

import com.smart.report.ReportException;
import org.junit.jupiter.api.Test;

import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.ServiceUIFactory;
import javax.print.attribute.Attribute;
import javax.print.attribute.AttributeSet;
import javax.print.attribute.PrintJobAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.PrintServiceAttribute;
import javax.print.attribute.PrintServiceAttributeSet;
import javax.print.attribute.HashPrintServiceAttributeSet;
import javax.print.attribute.standard.Media;
import javax.print.attribute.standard.MediaSizeName;
import javax.print.event.PrintJobAttributeListener;
import javax.print.event.PrintJobListener;
import javax.print.event.PrintServiceAttributeListener;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** درج النقدية وسؤال الطابعة عن المقاس، على طابعة مزيفة بتاخد بايتات خام. */
class ThermalPrinterTest {

    /** طابعة بتاخد بايتات خام (أو لأ)، وبتعلن المقاسات دي. */
    static final class RawPrinter implements PrintService {
        final boolean raw;
        final Media[] media;
        final List<byte[]> received = new ArrayList<>();

        RawPrinter(boolean raw, Media... media) {
            this.raw = raw;
            this.media = media;
        }

        @Override
        public String getName() {
            return "XP-80C";
        }

        @Override
        public DocPrintJob createPrintJob() {
            return new DocPrintJob() {
                @Override
                public PrintService getPrintService() {
                    return RawPrinter.this;
                }

                @Override
                public PrintJobAttributeSet getAttributes() {
                    return null;
                }

                @Override
                public void addPrintJobListener(PrintJobListener listener) {
                }

                @Override
                public void removePrintJobListener(PrintJobListener listener) {
                }

                @Override
                public void addPrintJobAttributeListener(PrintJobAttributeListener listener,
                                                         PrintJobAttributeSet attributes) {
                }

                @Override
                public void removePrintJobAttributeListener(PrintJobAttributeListener listener) {
                }

                @Override
                public void print(Doc doc, PrintRequestAttributeSet attributes) throws PrintException {
                    try {
                        Object data = doc.getPrintData();
                        if (data instanceof byte[] bytes) {
                            received.add(bytes.clone());
                        } else {
                            ByteArrayOutputStream out = new ByteArrayOutputStream();
                            ((InputStream) data).transferTo(out);
                            received.add(out.toByteArray());
                        }
                    } catch (IOException e) {
                        throw new PrintException(e);
                    }
                }
            };
        }

        @Override
        public void addPrintServiceAttributeListener(PrintServiceAttributeListener listener) {
        }

        @Override
        public void removePrintServiceAttributeListener(PrintServiceAttributeListener listener) {
        }

        @Override
        public PrintServiceAttributeSet getAttributes() {
            return new HashPrintServiceAttributeSet();
        }

        @Override
        public <T extends PrintServiceAttribute> T getAttribute(Class<T> category) {
            return null;
        }

        @Override
        public DocFlavor[] getSupportedDocFlavors() {
            return raw ? new DocFlavor[]{DocFlavor.BYTE_ARRAY.AUTOSENSE} : new DocFlavor[0];
        }

        @Override
        public boolean isDocFlavorSupported(DocFlavor flavor) {
            return raw && DocFlavor.BYTE_ARRAY.AUTOSENSE.equals(flavor);
        }

        @Override
        public Class<?>[] getSupportedAttributeCategories() {
            return new Class<?>[]{Media.class};
        }

        @Override
        public boolean isAttributeCategorySupported(Class<? extends Attribute> category) {
            return category == Media.class;
        }

        @Override
        public Object getDefaultAttributeValue(Class<? extends Attribute> category) {
            return null;
        }

        @Override
        public Object getSupportedAttributeValues(Class<? extends Attribute> category, DocFlavor flavor,
                                                  AttributeSet attributes) {
            return category == Media.class ? media : null;
        }

        @Override
        public boolean isAttributeValueSupported(Attribute attrval, DocFlavor flavor, AttributeSet attributes) {
            return false;
        }

        @Override
        public AttributeSet getUnsupportedAttributes(DocFlavor flavor, AttributeSet attributes) {
            return null;
        }

        @Override
        public ServiceUIFactory getServiceUIFactory() {
            return null;
        }
    }

    @Test
    void theDrawerGetsEscP() throws Exception {
        RawPrinter printer = new RawPrinter(true);
        PdfPrinter.sendRaw(printer, PdfPrinter.drawerPulse(PdfPrinter.DrawerPin.PIN_2, 100));
        PdfPrinter.sendRaw(printer, PdfPrinter.drawerPulse(PdfPrinter.DrawerPin.PIN_5, 60));
        assertEquals(2, printer.received.size());
        assertArrayEquals(new byte[]{0x1B, 0x70, 0, 50, 100}, printer.received.get(0));
        assertArrayEquals(new byte[]{0x1B, 0x70, 1, 30, 60}, printer.received.get(1));
    }

    @Test
    void pulseLimits() {
        assertArrayEquals(new byte[]{0x1B, 0x70, 0, 1, 50}, PdfPrinter.drawerPulse(PdfPrinter.DrawerPin.PIN_2, 2));
        assertArrayEquals(new byte[]{0x1B, 0x70, 0, (byte) 255, (byte) 255},
                PdfPrinter.drawerPulse(PdfPrinter.DrawerPin.PIN_2, 510));
        assertThrows(IllegalArgumentException.class, () -> PdfPrinter.drawerPulse(PdfPrinter.DrawerPin.PIN_2, 1));
        assertThrows(IllegalArgumentException.class, () -> PdfPrinter.drawerPulse(PdfPrinter.DrawerPin.PIN_2, 511));
        assertThrows(NullPointerException.class, () -> PdfPrinter.drawerPulse(null, 100));
    }

    /** درايفر ما بياخدش بايتات خام (طابعة مكتب أو PDF): رسالة واضحة، مش صمت. */
    @Test
    void aDriverWithoutRawBytesSaysSo() {
        RawPrinter office = new RawPrinter(false);
        ReportException refused = assertThrows(ReportException.class,
                () -> PdfPrinter.sendRaw(office, PdfPrinter.drawerPulse(PdfPrinter.DrawerPin.PIN_2, 100)));
        assertTrue(refused.getMessage().contains("raw bytes"), refused.getMessage());
        assertTrue(office.received.isEmpty());
    }

    @Test
    void aMissingPrinterIsNamed() {
        ReportException missing = assertThrows(ReportException.class,
                () -> PdfPrinter.openCashDrawer("No such printer 7f3a"));
        assertTrue(missing.getMessage().contains("No such printer 7f3a"), missing.getMessage());
    }

    @Test
    void whatAPrinterDeclares() {
        RawPrinter a4Only = new RawPrinter(true, MediaSizeName.ISO_A4, MediaSizeName.NA_LETTER);
        assertTrue(PdfPrinter.declares(a4Only, Paper.A4));
        assertTrue(PdfPrinter.declares(a4Only, Paper.A4.landscape()), "بالعرض نفس الورقة");
        assertFalse(PdfPrinter.declares(a4Only, Paper.A5));
        assertTrue(PdfPrinter.declares(a4Only, Paper.ROLL_80), "اللفة ما بيتطلبش لها مقاس");
        assertTrue(PdfPrinter.declares(new RawPrinter(true), Paper.A5), "ما بتعلنش حاجة: ما نقدرش نعرف");
    }
}
