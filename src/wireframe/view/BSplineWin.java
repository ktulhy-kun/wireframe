package wireframe.view;

import wireframe.Figure3D;
import wireframe.Model;
import wireframe.pixel.Point2DI;
import wireframe.matrix.Vector;
import wireframe.matrix.errors.VectorDimensionException;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.Method;
import java.util.*;
import java.util.List;

public class BSplineWin extends JDialog {
    private GraphViewPanel graphViewPanel;
    private JPanel paramsPanel = new JPanel();
    private JPanel buttonsPanel = new JPanel();
    private Model model;
    private HashMap<String, Component> componentMap;

    BSplineWin(Model model) {
        setTitle("B-сплайн");
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setLayout(new GridBagLayout());
        this.model = model;

        initGraphViewPanel();
        panelsInit();

        addAllParams();
        addAllButtons();

        createComponentMap();

        pack();
        setSize(640, 480);
    }

    // INIT'S

    private void initGraphViewPanel() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.FIRST_LINE_START;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1;
        gbc.weighty = .89;
        gbc.gridx = 0;
        gbc.gridy = 0;

        graphViewPanel = new GraphViewPanel(model);

        graphViewPanel.setFigure3D(model.getFigure(0));
        add(graphViewPanel, gbc);
    }

    private void panelsInit() {
        paramsPanel.setLayout(new GridLayout(3, 10));
        buttonsPanel.setLayout(new GridLayout(1, 3));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.FIRST_LINE_START;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1;
        gbc.weighty = .1;
        gbc.gridx = 0;
        gbc.gridy = 1;

        add(paramsPanel, gbc);
        gbc.weighty = 0.01;
        gbc.gridy = 2;
        add(buttonsPanel, gbc);

        paramsPanel.setVisible(true);
        buttonsPanel.setVisible(true);
    }

    // HELPERS

    private void createComponentMap() {
        componentMap = new HashMap<String,Component>();
        Component[] components = paramsPanel.getComponents();
        for (Component component : components) {
            componentMap.put(component.getName(), component);
        }
    }

    public Component getComponentByName(String name) {
        if (componentMap.containsKey(name)) {
            return (Component) componentMap.get(name);
        }
        else return null;
    }

    private JFormattedTextField addInputField(String label, Object value) {
        JLabel jLabel = new JLabel(label);
        jLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        JFormattedTextField ftf = new JFormattedTextField(value);
        ftf.setName(label);

        paramsPanel.add(jLabel);
        paramsPanel.add(ftf);
        return ftf;
    }

    private JFormattedTextField addInputField(String label, Object value, String actionMethod) {
        JFormattedTextField ftf = addInputField(label, value);
        try {
            final Method method = getClass().getMethod(actionMethod);
            ftf.addPropertyChangeListener(new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent evt) {
                    try {
                        method.invoke(BSplineWin.this);
                        graphViewPanel.reCalcObjects();
                        graphViewPanel.repaint();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        return ftf;
    }

    private JButton addButton(String label) {
        JButton btn = new JButton(label);
        btn.setName(label);
        buttonsPanel.add(btn);
        return btn;
    }

    private void addButton(String label, String actionMethod) {
        JButton btn = addButton(label);
        try {
            final Method method = getClass().getMethod(actionMethod);
            btn.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    try {
                        method.invoke(BSplineWin.this);
                    } catch (Exception err) {
                        throw new RuntimeException(err);
                    }
                }
            });
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    // ADDING

    private void addAllParams() {
        int[] intParams = model.getIntParams();
        addInputField("n", intParams[0], "updateIntParams");
        addInputField("m", intParams[1], "updateIntParams");
        addInputField("k", intParams[2], "updateIntParams");

        addInputField("№", 0);
        addInputField("R", 0);
        addInputField("a", 0);
        addInputField("b", 1);
        addInputField("c", 0);
        addInputField("d", 6.28);
        addInputField("G", 0);
        addInputField("zn", 10);
        addInputField("zf", 15);
        addInputField("sw", 1);
        addInputField("sh", 1);
        addInputField("B", 255);
    }

    private void addAllButtons() {
        addButton("Ok");
        addButton("Apply", "applyButton");
        addButton("Cancel");
    }

    // CONTROLLER

    public void updateIntParams() {
        int[] intParams = new int[3];
        intParams[0] = (int) ((JFormattedTextField) getComponentByName("n")).getValue();
        intParams[1] = (int) ((JFormattedTextField) getComponentByName("m")).getValue();
        intParams[2] = (int) ((JFormattedTextField) getComponentByName("k")).getValue();
        model.setIntParams(intParams);
    }

    public void applyButton() {
        graphViewPanel.applyFigure();
    }
}

class GraphViewPanel extends JPanel {
    private final Model model;
    private BufferedImage img;
    private Figure3D figure3D;
    private Figure3D figure3DOrigin;
    private double drawK = 0.; // сколько пикселей в 1чке
    private Point2DI size = new Point2DI(0, 0);
    private Vector centerV = new Vector(0, 0);
    private Point2DI centerP;
    private final int MIN_POINT_RECT_SIZE = 9;
    private int pointRectSide = 0;
    private int middleCircleSize = 0;

    private int curMovingPointIndex = -1;

    private List<Point2DI> points;
    private List<Point2DI> middlePoints;

    GraphViewPanel(Model model) {
        super();
        this.model = model;
        sizeChanged();
        setMinimumSize(new Dimension(100, 100));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        sizeChanged();
        calcDrawK();

        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, size.getX(), size.getY());

        g2d.setColor(Color.WHITE);
        g2d.drawLine(0, centerP.getY(), size.getX(), centerP.getY());
        g2d.drawLine(centerP.getX(), 0, centerP.getX(), size.getY());
        reCalcObjects();
        drawMarks(g2d);
        drawBSpline(g2d);

        mouseListenerInit();
        componentListenerInit();
    }

    // GRAPHIC HELP

    private void componentListenerInit() {
        addComponentListener(new ComponentListener() {
            @Override
            public void componentResized(ComponentEvent e) {
                sizeChanged(e.getComponent().getWidth(), e.getComponent().getHeight());
            }

            @Override
            public void componentMoved(ComponentEvent e) {

            }

            @Override
            public void componentShown(ComponentEvent e) {

            }

            @Override
            public void componentHidden(ComponentEvent e) {

            }
        });
    }

    public void sizeChanged(int width, int height) {
        if ((size.getX() == width) && (size.getY() == height)) {
            return;
        }
        size.setX(width);
        size.setY(height);
        pointRectSide = Math.max(Math.min(width, height) / 300, MIN_POINT_RECT_SIZE);
        middleCircleSize = pointRectSide * 2 / 3;

        centerV.setX(width / 2);
        centerV.setY(height / 2);
        centerP = centerV.getPoint2DI();
    }

    private void sizeChanged() {
        Dimension size = getSize();
        sizeChanged(size.width, size.height);
    }

    private void calcDrawK() {
        Vector[] envRect = figure3D.bSpline.getEnvRect();
        Vector leftUp = envRect[0];
        Vector rightDown = envRect[1];

        double maxX = Math.max(Math.abs(leftUp.getX()), Math.abs(rightDown.getX()));
        double maxY = Math.max(Math.abs(leftUp.getY()), Math.abs(rightDown.getY()));

        drawK = Math.min(centerP.getX() * 0.9 / maxX, centerP.getY() * 0.9 / maxY);
    }

    private void mouseListenerInit() {
        addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                if (curMovingPointIndex != -1) {
                    return;
                }

                Point2DI p = new Point2DI(e.getX(), e.getY());
                curMovingPointIndex = getPointIndex(points, pointRectSide, pointRectSide, p);
                if (curMovingPointIndex != -1) {
                    if (e.getButton() == 3) {
                        figure3D.bSpline.deletePoint(curMovingPointIndex);
                        curMovingPointIndex = -1;
                        reCalcObjects();
                    }
                } else {
                    int addingIndex = getPointIndex(middlePoints,
                            middleCircleSize,
                            middleCircleSize,
                            p) + 1;
                    if (addingIndex != 0) {
                        figure3D.bSpline.addPoint(addingIndex, pointToValue(p.getX(), p.getY()));
                        curMovingPointIndex = addingIndex;
                    }
                }
                repaint();
            }
            public void mouseReleased(MouseEvent e) {
                curMovingPointIndex = -1;
                repaint();
            }
        });

        addMouseMotionListener(new MouseAdapter() {
            public void mouseDragged(MouseEvent e) {
                if (curMovingPointIndex != -1) {
                    figure3D.bSpline.movePoint(curMovingPointIndex, pointToValue(e.getX(), e.getY()));
                }
                repaint();
            }
        });
    }

    public int getPointIndex(List<Point2DI> list, int width, int height, Point2DI curPoint) {
        for (int i = 0; i < list.size(); ++i) {
            Point2DI lPoint = list.get(i);
            if (
                    ((lPoint.getX() - width / 2 - 1) < curPoint.getX())
                    && (curPoint.getX()  < (lPoint.getX() + width / 2 + 1))
                    && ((lPoint.getY() - height / 2 - 1) < curPoint.getY())
                    && (curPoint.getY() < (lPoint.getY() + height / 2 + 1))
                    ) {
                return i;
            }
        }
        return -1;
    }

    private Vector valueToPoint(Vector v) {
        Vector vTemp = new Vector(v);
        vTemp.multiple(drawK);
        try {
            vTemp = vTemp.move(centerV);
            return vTemp;
        } catch (VectorDimensionException e) {
            e.printStackTrace();
        }
        return null;
    }

    private Vector pointToValue(int x, int y) {
        try {
            Vector pTemp = (new Vector(x, y)).move(centerV, -1);
            pTemp.multiple(1 / drawK);
            return pTemp;
        } catch (VectorDimensionException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void drawLine(Graphics2D g2d, Point2DI p1, Point2DI p2) {
        g2d.drawLine(
                p1.getX(), p1.getY(),
                p2.getX(), p2.getY()
        );
    }

    private void drawPointRect(Graphics2D g2d, Point2DI p) {
        g2d.drawRect(
                p.getX() - pointRectSide / 2, p.getY() - pointRectSide / 2,
                pointRectSide, pointRectSide
        );
    }

    private void drawMiddleCircle(Graphics2D g2d, Point2DI p) {
        g2d.drawArc(p.getX() - middleCircleSize / 2, p.getY() - middleCircleSize / 2,
                middleCircleSize, middleCircleSize, 0, 360);
    }

    private void drawBSpline(Graphics2D g2d) {
        g2d.setColor(Color.red);

        Iterator<Point2DI> pointsIterator = points.iterator();

        Point2DI prevPoint = pointsIterator.next();
        drawPointRect(g2d, prevPoint);


        for (; pointsIterator.hasNext();) {
            Point2DI point = pointsIterator.next();

            drawPointRect(g2d, point);
            drawLine(g2d, prevPoint, point);

            prevPoint = point;
        }

        for (Point2DI mPoint: middlePoints) {
            drawMiddleCircle(g2d, mPoint);
        }

        prevPoint = null;

        int pointI = 0;

        for (double l = model.getA(); l < model.getB(); l += model.getdL()) {
            Point2DI point = valueToPoint(
                    figure3D.bSpline.calcL(
                            l * figure3D.bSpline.getLen()
                    )
            ).getPoint2DI();

            if (prevPoint == null) {
                prevPoint = point;
            }

            g2d.setColor(figure3D.color);
            g2d.drawLine(prevPoint.getX(), prevPoint.getY(), point.getX(), point.getY());

            if (pointI % model.getK() == 0) {
                g2d.setColor(figure3D.color.brighter().brighter());
                g2d.drawLine(prevPoint.getX(), prevPoint.getY(), prevPoint.getX(), prevPoint.getY());
            }

            prevPoint = point;
            pointI += 1;
        }

    }

    private void drawMarks(Graphics2D g2d) {
        g2d.setColor(Color.WHITE);
        for (int i = 0; i < centerP.getX() / drawK; ++i) {
            g2d.drawLine((int) (centerP.getX() - i * drawK), centerP.getY() - pointRectSide / 2,
                    (int) (centerP.getX() - i * drawK),  centerP.getY() + pointRectSide / 2);

            g2d.drawLine((int) (centerP.getX() + i * drawK), centerP.getY() - pointRectSide / 2,
                    (int) (centerP.getX() + i * drawK),  centerP.getY() + pointRectSide / 2);
        }

        for (int i = 0; i < centerP.getY() / drawK; ++i) {
            g2d.drawLine(centerP.getX() - pointRectSide / 2, (int) (centerP.getY() - i * drawK),
                    centerP.getX() + pointRectSide / 2, (int) (centerP.getY() - i * drawK));
            g2d.drawLine(centerP.getX() - pointRectSide / 2, (int) (centerP.getY() + i * drawK),
                    centerP.getX() + pointRectSide / 2, (int) (centerP.getY() + i * drawK));
        }
    }

    public void reCalcObjects() {
        points = new ArrayList<>();
        middlePoints = new ArrayList<>();

        for (Iterator<Vector> vectorIterator = figure3D.bSpline.getPointsIterator(); vectorIterator.hasNext();) {
            points.add(valueToPoint(vectorIterator.next()).getPoint2DI());
        }

        Iterator<Point2DI> pointsIterator = points.iterator();
        Point2DI prevPoint = pointsIterator.next();
        for (; pointsIterator.hasNext();) {
            Point2DI point = pointsIterator.next();
            middlePoints.add(prevPoint.divided(point, 1));
            prevPoint = point;
        }
    }

    public void setFigure3D(Figure3D figure3D) {
        this.figure3DOrigin = figure3D;
        this.figure3D = new Figure3D(this.figure3DOrigin);
        reCalcObjects();
    }

    // Buttons

    public void applyFigure() {
        figure3DOrigin.apply(figure3D);
        figure3DOrigin.calcPoints(model);
    }
}
