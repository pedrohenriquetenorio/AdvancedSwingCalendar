package br.com.advancedswingcalendar;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.beans.BeanProperty;
import java.beans.JavaBean;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

/**
 * Calendário grande. Pode selecionar uma data (SINGLE) ou um período (RANGE),
 * mostrando 1 a 3 meses lado a lado. As cores vêm do UIManager, então acompanha
 * o tema claro/escuro do FlatLaf.
 */
@JavaBean(description = "Calendário para seleção de data ou período", defaultProperty = "advSelectionMode")
public class AdvCalendar extends JPanel {

    private AdvSelectionMode advSelectionMode = AdvSelectionMode.RANGE;
    private int advMonthsShown = 2;
    private LocalDate advStartDate;
    private LocalDate advEndDate;
    private LocalDate advMinDate;
    private LocalDate advMaxDate;
    private DayOfWeek advFirstDayOfWeek = DayOfWeek.SUNDAY;
    private Locale advLocale = Locale.forLanguageTag("pt-BR");
    private int advCellSize = 36;
    private int advCellArc = 12;
    private boolean advHighlightToday = true;

    private YearMonth viewMonth = YearMonth.now();
    private LocalDate hoverDate;

    private final List<MonthGrid> grids = new ArrayList<>();
    private final List<JLabel> titles = new ArrayList<>();

    public AdvCalendar() {
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        rebuild();
    }

    // ------------------------------------------------------------------ UI

    private void rebuild() {
        removeAll();
        grids.clear();
        titles.clear();
        setLayout(new GridLayout(1, advMonthsShown, 18, 0));

        for (int i = 0; i < advMonthsShown; i++) {
            JPanel column = new JPanel(new BorderLayout(0, 6));
            column.setOpaque(false);

            JLabel title = new JLabel("", SwingConstants.CENTER);
            titles.add(title);

            JPanel header = new JPanel(new BorderLayout());
            header.setOpaque(false);
            header.add(i == 0 ? navBox("«", -12, "‹", -1) : spacer(), BorderLayout.WEST);
            header.add(title, BorderLayout.CENTER);
            header.add(i == advMonthsShown - 1 ? navBox("›", 1, "»", 12) : spacer(), BorderLayout.EAST);

            MonthGrid grid = new MonthGrid(i);
            grids.add(grid);

            column.add(header, BorderLayout.NORTH);
            column.add(grid, BorderLayout.CENTER);
            add(column);
        }
        updateTitles();
        revalidate();
        repaint();
    }

    private static final int NAV_SIZE = 28;

    private JPanel navBox(String textA, int deltaA, String textB, int deltaB) {
        JPanel box = new JPanel(new GridLayout(1, 2));
        box.setOpaque(false);
        box.add(navButton(textA, deltaA));
        box.add(navButton(textB, deltaB));
        return box;
    }

    private JPanel spacer() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setPreferredSize(new Dimension(NAV_SIZE * 2, NAV_SIZE));
        return p;
    }

    private JButton navButton(String text, int deltaMonths) {
        JButton b = new JButton(text);
        b.setFocusable(false);
        b.setMargin(new Insets(0, 0, 0, 0));
        b.putClientProperty("JButton.buttonType", "toolBarButton"); // FlatLaf (ignorado em outros LAFs)
        b.setPreferredSize(new Dimension(NAV_SIZE, NAV_SIZE));
        b.addActionListener(e -> {
            viewMonth = viewMonth.plusMonths(deltaMonths);
            updateTitles();
            repaint();
        });
        return b;
    }

    private void updateTitles() {
        Font base = UIManager.getFont("Label.font");
        for (int i = 0; i < titles.size(); i++) {
            YearMonth m = viewMonth.plusMonths(i);
            JLabel t = titles.get(i);
            t.setText(capitalize(m.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, advLocale)) + " " + m.getYear());
            if (base != null) {
                t.setFont(base.deriveFont(Font.BOLD));
            }
        }
    }

    private static String capitalize(String s) {
        return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String shortDayName(DayOfWeek d) {
        String s = d.getDisplayName(TextStyle.SHORT, advLocale).replace(".", "");
        if (s.length() > 3) {
            s = s.substring(0, 3);
        }
        return capitalize(s);
    }

    // ------------------------------------------------------------------ cores

    static Color accent() {
        Color c = UIManager.getColor("Component.accentColor");
        if (c == null) {
            c = UIManager.getColor("Button.default.background");
        }
        if (c == null) {
            c = UIManager.getColor("List.selectionBackground");
        }
        return c != null ? c : new Color(0x2F6FED);
    }

    private static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }

    // ------------------------------------------------------------------ lógica

    private boolean isAllowed(LocalDate d) {
        return (advMinDate == null || !d.isBefore(advMinDate)) && (advMaxDate == null || !d.isAfter(advMaxDate));
    }

    private void dayClicked(LocalDate d) {
        if (!isEnabled() || !isAllowed(d)) {
            return;
        }
        LocalDate oldStart = advStartDate;
        LocalDate oldEnd = advEndDate;

        if (advSelectionMode == AdvSelectionMode.SINGLE) {
            advStartDate = d;
            advEndDate = null;
        } else if (advStartDate == null || advEndDate != null) {
            advStartDate = d;
            advEndDate = null;
        } else if (d.isBefore(advStartDate)) {
            advEndDate = advStartDate;
            advStartDate = d;
        } else {
            advEndDate = d;
        }
        repaint();
        fireSelectionChanged(oldStart, oldEnd);
    }

    private void fireSelectionChanged(LocalDate oldStart, LocalDate oldEnd) {
        firePropertyChange("advStartDate", oldStart, advStartDate);
        firePropertyChange("advEndDate", oldEnd, advEndDate);
        if (java.util.Objects.equals(oldStart, advStartDate) && java.util.Objects.equals(oldEnd, advEndDate)) {
            return;
        }
        AdvDateSelectionEvent evt = new AdvDateSelectionEvent(this, advStartDate, advEndDate);
        for (AdvDateSelectionListener l : listenerList.getListeners(AdvDateSelectionListener.class)) {
            l.dateSelectionChanged(evt);
        }
    }

    /** Mostra o mês informado como primeiro mês visível. */
    public void showMonth(YearMonth month) {
        if (month != null) {
            viewMonth = month;
            updateTitles();
            repaint();
        }
    }

    private void ensureVisible(LocalDate d) {
        if (d == null) {
            return;
        }
        YearMonth ym = YearMonth.from(d);
        if (ym.isBefore(viewMonth) || ym.isAfter(viewMonth.plusMonths(advMonthsShown - 1L))) {
            showMonth(ym);
        }
    }

    /** Remove a seleção atual. */
    public void clearSelection() {
        LocalDate oldStart = advStartDate;
        LocalDate oldEnd = advEndDate;
        advStartDate = null;
        advEndDate = null;
        repaint();
        fireSelectionChanged(oldStart, oldEnd);
    }

    /** Define o período por código (troca as datas se vierem invertidas). */
    public void setAdvRange(LocalDate start, LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) {
            LocalDate t = start;
            start = end;
            end = t;
        }
        LocalDate oldStart = advStartDate;
        LocalDate oldEnd = advEndDate;
        advStartDate = start;
        advEndDate = advSelectionMode == AdvSelectionMode.SINGLE ? null : end;
        ensureVisible(advStartDate);
        repaint();
        fireSelectionChanged(oldStart, oldEnd);
    }

    // ------------------------------------------------------------------ eventos

    public void addAdvDateSelectionListener(AdvDateSelectionListener l) {
        listenerList.add(AdvDateSelectionListener.class, l);
    }

    public void removeAdvDateSelectionListener(AdvDateSelectionListener l) {
        listenerList.remove(AdvDateSelectionListener.class, l);
    }

    // ------------------------------------------------------------------ propriedades

    public AdvSelectionMode getAdvSelectionMode() {
        return advSelectionMode;
    }

    public void setAdvSelectionMode(AdvSelectionMode mode) {
        AdvSelectionMode old = advSelectionMode;
        advSelectionMode = mode == null ? AdvSelectionMode.RANGE : mode;
        if (advSelectionMode == AdvSelectionMode.SINGLE && advEndDate != null) {
            LocalDate oldEnd = advEndDate;
            advEndDate = null;
            fireSelectionChanged(advStartDate, oldEnd);
        }
        firePropertyChange("advSelectionMode", old, advSelectionMode);
        repaint();
    }

    public int getAdvMonthsShown() {
        return advMonthsShown;
    }

    public void setAdvMonthsShown(int months) {
        int old = advMonthsShown;
        advMonthsShown = Math.max(1, Math.min(3, months));
        if (old != advMonthsShown) {
            rebuild();
            firePropertyChange("advMonthsShown", old, advMonthsShown);
        }
    }

    @BeanProperty(hidden = true)
    public LocalDate getAdvStartDate() {
        return advStartDate;
    }

    @BeanProperty(hidden = true)
    public LocalDate getAdvEndDate() {
        return advEndDate;
    }

    /** Atalho para o modo SINGLE: data selecionada (= data inicial). */
    @BeanProperty(hidden = true)
    public LocalDate getAdvSelectedDate() {
        return advStartDate;
    }

    public void setAdvSelectedDate(LocalDate date) {
        setAdvRange(date, null);
    }

    @BeanProperty(hidden = true)
    public LocalDate getAdvMinDate() {
        return advMinDate;
    }

    public void setAdvMinDate(LocalDate d) {
        advMinDate = d;
        repaint();
    }

    @BeanProperty(hidden = true)
    public LocalDate getAdvMaxDate() {
        return advMaxDate;
    }

    public void setAdvMaxDate(LocalDate d) {
        advMaxDate = d;
        repaint();
    }

    public DayOfWeek getAdvFirstDayOfWeek() {
        return advFirstDayOfWeek;
    }

    public void setAdvFirstDayOfWeek(DayOfWeek d) {
        advFirstDayOfWeek = d == null ? DayOfWeek.SUNDAY : d;
        repaint();
    }

    @BeanProperty(hidden = true)
    public Locale getAdvLocale() {
        return advLocale;
    }

    public void setAdvLocale(Locale locale) {
        advLocale = locale == null ? Locale.forLanguageTag("pt-BR") : locale;
        updateTitles();
        repaint();
    }

    public int getAdvCellSize() {
        return advCellSize;
    }

    public void setAdvCellSize(int size) {
        advCellSize = Math.max(24, size);
        revalidate();
        repaint();
    }

    public int getAdvCellArc() {
        return advCellArc;
    }

    /** Arredondamento das células selecionadas (999 = círculo). */
    public void setAdvCellArc(int arc) {
        advCellArc = Math.max(0, arc);
        repaint();
    }

    public boolean isAdvHighlightToday() {
        return advHighlightToday;
    }

    public void setAdvHighlightToday(boolean b) {
        advHighlightToday = b;
        repaint();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        repaint();
    }

    // ------------------------------------------------------------------ grade do mês

    private final class MonthGrid extends JComponent {

        private final int index;

        MonthGrid(int index) {
            this.index = index;
            setOpaque(false);
            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    LocalDate d = dateAt(e.getPoint());
                    if (!java.util.Objects.equals(d, hoverDate)) {
                        hoverDate = d;
                        AdvCalendar.this.repaint();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    if (hoverDate != null) {
                        hoverDate = null;
                        AdvCalendar.this.repaint();
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (e.getButton() == MouseEvent.BUTTON1) {
                        LocalDate d = dateAt(e.getPoint());
                        if (d != null) {
                            dayClicked(d);
                        }
                    }
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        private YearMonth month() {
            return viewMonth.plusMonths(index);
        }

        private int offset() {
            int first = month().atDay(1).getDayOfWeek().getValue();
            return (first - advFirstDayOfWeek.getValue() + 7) % 7;
        }

        private LocalDate dateAt(java.awt.Point p) {
            double cw = getWidth() / 7.0;
            double ch = getHeight() / 7.0;
            int col = (int) (p.x / cw);
            int row = (int) (p.y / ch) - 1;
            if (col < 0 || col > 6 || row < 0 || row > 5) {
                return null;
            }
            int day = row * 7 + col - offset() + 1;
            if (day < 1 || day > month().lengthOfMonth()) {
                return null;
            }
            return month().atDay(day);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(advCellSize * 7, advCellSize * 7);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            double cw = getWidth() / 7.0;
            double ch = getHeight() / 7.0;
            Color fg = UIManager.getColor("Label.foreground");
            Color muted = UIManager.getColor("Label.disabledForeground");
            if (fg == null) {
                fg = Color.BLACK;
            }
            if (muted == null) {
                muted = Color.GRAY;
            }
            Color accent = accent();
            boolean on = AdvCalendar.this.isEnabled();

            Font base = AdvCalendar.this.getFont();
            Font small = base.deriveFont(Font.PLAIN, base.getSize2D() - 1f);
            Font bold = base.deriveFont(Font.BOLD);

            // cabeçalho (dias da semana)
            g.setFont(small);
            g.setColor(muted);
            for (int c = 0; c < 7; c++) {
                drawCentered(g, shortDayName(advFirstDayOfWeek.plus(c)), c * cw, 0, cw, ch);
            }

            // período a desenhar (inclui pré-visualização ao passar o mouse)
            LocalDate a = advStartDate;
            LocalDate b = advEndDate;
            boolean preview = false;
            if (advSelectionMode == AdvSelectionMode.RANGE && a != null && b == null && hoverDate != null && on) {
                preview = true;
                if (hoverDate.isBefore(a)) {
                    b = a;
                    a = hoverDate;
                } else {
                    b = hoverDate;
                }
            }

            YearMonth ym = month();
            int last = ym.lengthOfMonth();
            int off = offset();
            LocalDate today = LocalDate.now();
            double pad = 3;
            double bandH = ch - pad * 2;
            double arc = Math.min(advCellArc, Math.min(bandH, cw - pad * 2));

            for (int day = 1; day <= last; day++) {
                LocalDate d = ym.atDay(day);
                int idx = off + day - 1;
                int row = idx / 7;
                int col = idx % 7;
                double x = col * cw;
                double y = (row + 1) * ch;

                boolean allowed = on && isAllowed(d);
                boolean isStart = d.equals(a);
                boolean isEnd = b != null && d.equals(b);
                boolean inside = a != null && b != null && d.isAfter(a) && d.isBefore(b);
                boolean rowStart = col == 0 || day == 1;
                boolean rowEnd = col == 6 || day == last;

                // faixa do período
                if (a != null && b != null && !a.equals(b) && (inside || isStart || isEnd)) {
                    double x0 = isStart ? x + cw / 2 : x;
                    double x1 = isEnd ? x + cw / 2 : x + cw;
                    boolean roundL = x0 == x && rowStart;
                    boolean roundR = x1 == x + cw && rowEnd;
                    Shape oldClip = g.getClip();
                    g.clip(new Rectangle2D.Double(x0, y + pad, x1 - x0, bandH));
                    g.setColor(withAlpha(accent, preview ? 30 : 55));
                    double bx = x - (roundL ? 0 : arc);
                    double bw = cw + (roundL ? 0 : arc) + (roundR ? 0 : arc);
                    g.fill(new RoundRectangle2D.Double(bx, y + pad, bw, bandH, arc, arc));
                    g.setClip(oldClip);
                }

                double side = Math.min(cw, ch) - pad * 2;
                RoundRectangle2D cell = new RoundRectangle2D.Double(x + (cw - side) / 2, y + (ch - side) / 2, side, side,
                        Math.min(advCellArc, side), Math.min(advCellArc, side));

                boolean selected = isStart || isEnd;
                if (selected) {
                    g.setColor(allowed || preview ? accent : withAlpha(accent, 120));
                    g.fill(cell);
                } else if (allowed && d.equals(hoverDate)) {
                    g.setColor(withAlpha(accent, 40));
                    g.fill(cell);
                }

                if (advHighlightToday && d.equals(today) && !selected) {
                    g.setColor(accent);
                    g.setStroke(new java.awt.BasicStroke(1.4f));
                    g.draw(cell);
                }

                g.setFont(d.equals(today) && advHighlightToday ? bold : base);
                g.setColor(selected ? Color.WHITE : (allowed ? fg : muted));
                drawCentered(g, String.valueOf(day), x, y, cw, ch);
            }
            g.dispose();
        }

        private void drawCentered(Graphics2D g, String text, double x, double y, double w, double h) {
            FontMetrics fm = g.getFontMetrics();
            float tx = (float) (x + (w - fm.stringWidth(text)) / 2.0);
            float ty = (float) (y + (h - fm.getHeight()) / 2.0 + fm.getAscent());
            g.drawString(text, tx, ty);
        }
    }
}
