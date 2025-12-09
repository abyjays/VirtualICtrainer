// VirtualICTrainer.java
// Compile (PowerShell):
// javac --module-path "C:\Program Files\Java\Javafx\lib" --add-modules javafx.controls,javafx.fxml VirtualICTrainer.java
// Run:
// java --module-path "C:\Program Files\Java\Javafx\lib" --add-modules javafx.controls,javafx.fxml VirtualICTrainer

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.Circle;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeLineCap;
import javafx.animation.*;

import javafx.util.Duration;

import javafx.scene.text.*;
import javafx.stage.*;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class VirtualICTrainer extends Application {

    private Pane board;
    private ToggleButton powerToggle;
    private final List<ExternalSwitch> externalSwitches = new ArrayList<>();
    private final List<ExternalLED> externalLEDs = new ArrayList<>();
    private final List<ICBase> ics = new ArrayList<>();
    private final List<Wire> wires = new ArrayList<>();
    private Pin connectionStart = null;
    private PowerNode vccNode = null;
private PowerNode gndNode = null;

    
    private final Deque<ExternalLED> undoStack = new ArrayDeque<>();

    private final Color[] palette = {
        Color.web("#00ffff"), Color.web("#ff6b6b"),
        Color.web("#2ecc71"), Color.web("#f1c40f"),
        Color.web("#9b59b6"), Color.web("#e67e22")
    };
    private int paletteIndex = 0;

    @Override
    public void start(Stage stage) {
        board = new Pane();
        board.setPrefSize(1400, 820);
        board.setStyle("-fx-background-color: linear-gradient(to bottom right,#1e272e,#2f3640);");

// =====================================
// ✅ TOOLBAR LAYOUT (CLEAN + FUNCTIONAL)
// =====================================

ToolBar toolbar = new ToolBar();
toolbar.setLayoutX(12);
toolbar.setLayoutY(12);
toolbar.setPadding(new Insets(5, 8, 5, 8));
toolbar.setStyle("-fx-background-color:linear-gradient(#3a3a3a,#222);-fx-border-color:#555;-fx-border-radius:6;");
toolbar.setEffect(new DropShadow(8, Color.color(0, 0, 0, 0.5)));

// --- Power + Clock ---
powerToggle = new ToggleButton("Power");
powerToggle.setOnAction(e -> togglePower());

Button clockBtn = new Button("Clock Pulse");
clockBtn.setOnAction(e -> triggerClockPulse());

// --- Logic Gates Menu ---
MenuButton logicMenu = new MenuButton("Logic Gates");
logicMenu.getItems().addAll(
    createICMenuItem("7400 NAND"),
    createICMenuItem("7402 NOR"),
    createICMenuItem("7408 AND"),
    createICMenuItem("7432 OR"),
    createICMenuItem("7404 NOT"),
    createICMenuItem("7486 XOR"),
    createICMenuItem("7487 XNOR")
);

// --- 3-Input Gates Menu ---
MenuButton logic3Menu = new MenuButton("3-Input Gates");
logic3Menu.getItems().addAll(
    createICMenuItem("AND3"),
    createICMenuItem("OR3"),
    createICMenuItem("NAND3"),
    createICMenuItem("NOR3"),
    createICMenuItem("XOR3"),
    createICMenuItem("XNOR3")
);

// --- Combinational Circuits Menu ---
MenuButton comboMenu = new MenuButton("Combinational Circuits");
comboMenu.getItems().addAll(
    createICMenuItem("2x1 MUX"),
    createICMenuItem("4x1 MUX"),
    createICMenuItem("8x1 MUX"),
    new SeparatorMenuItem(),
    createICMenuItem("1x2 DEMUX"),
    createICMenuItem("1x4 DEMUX"),
    createICMenuItem("1x8 DEMUX"),
    new SeparatorMenuItem(),
    createICMenuItem("4x2 ENCODER"),
    createICMenuItem("2x4 DECODER")
);

// --- Utility Buttons ---
Button truthBtn = new Button("Truth Table");
truthBtn.setOnAction(e -> showTruthTable());

ToggleButton simulateBtn = new ToggleButton("Simulate Circuit");
simulateBtn.setOnAction(e -> toggleSimulation(simulateBtn));

Button clearBtn = new Button("Clear Wires");
clearBtn.setOnAction(e -> clearConnections());

Button saveBtn = new Button("Save");
saveBtn.setOnAction(e -> saveCircuit(stage));

Button loadBtn = new Button("Load");
loadBtn.setOnAction(e -> loadCircuit(stage));

// --- Add All to Toolbar ---
toolbar.getItems().addAll(
    powerToggle,
    clockBtn,
    new Separator(),
    logicMenu,
    logic3Menu,
    comboMenu,
    new Separator(),
    truthBtn,
    simulateBtn,
    new Separator(),
    clearBtn,
    saveBtn,
    loadBtn
);

// Make it wrap properly if window resizes
toolbar.setPrefHeight(40);
toolbar.setStyle("-fx-font-size: 13px; -fx-background-color: linear-gradient(to right, #202020, #383838); -fx-text-fill: white;");


// --- Group into Combinational Circuits dropdown ---
MenuButton combinationalMenu = new MenuButton("Combinational Circuits");
combinationalMenu.getItems().addAll(
    new MenuItem("2x1 MUX"), new MenuItem("4x1 MUX"), new MenuItem("8x1 MUX"),
    new SeparatorMenuItem(),
    new MenuItem("1x2 DEMUX"), new MenuItem("1x4 DEMUX"), new MenuItem("1x8 DEMUX"),
    new SeparatorMenuItem(),
    new MenuItem("4x2 ENCODER"), new MenuItem("2x4 DECODER")
);

// --- Attach logic for each menu item (we’ll fill in later) ---
for (MenuItem item : combinationalMenu.getItems()) {
    if (item instanceof SeparatorMenuItem) continue;
    item.setOnAction(e -> {
        String name = item.getText();
        ICBase ic = switch (name) {
            case "2x1 MUX" -> new IC_MUX2x1(200, 550);
            case "4x1 MUX" -> new IC_MUX4x1(400, 550);
            case "8x1 MUX" -> new IC_MUX8x1(600, 550);
            case "1x2 DEMUX" -> new IC_DEMUX1x2(200, 650);
            case "1x4 DEMUX" -> new IC_DEMUX1x4(400, 650);
            case "1x8 DEMUX" -> new IC_DEMUX1x8(600, 650);
            case "4x2 ENCODER" -> new IC_ENC4x2(800, 550);
            case "2x4 DECODER" -> new IC_DEC2x4(1000, 550);
            default -> null;
        };
        if (ic != null) placeIC(ic);
    });
}

board.getChildren().add(toolbar);


        for(int i=0;i<8;i++){
            ExternalSwitch s=new ExternalSwitch(40,100+i*60,i+1);
            externalSwitches.add(s);
            board.getChildren().add(s.group);
            makeDraggable(s.group);
        }

        // Outputs
        for(int i=0;i<8;i++){
            ExternalLED l=new ExternalLED(1220,120+i*60,i+1);
            externalLEDs.add(l);
            board.getChildren().add(l.group);
        }

        Scene scene=new Scene(board);
        stage.setResizable(false);
scene.setFill(Color.web("#1e272e"));
stage.getIcons().add(new javafx.scene.image.Image("https://cdn-icons-png.flaticon.com/512/1483/1483336.png"));

        scene.setOnKeyPressed(k->{
            if(k.getCode()==KeyCode.DELETE&&selectedWire!=null){
                selectedWire.remove();wires.remove(selectedWire);selectedWire=null;evaluateAll();
            }
        });
        stage.setScene(scene);
        stage.setTitle("Virtual IC Trainer – Dark PCB Edition");
        stage.show();
    }

    private Wire selectedWire;
    // ---------- PIN & WIRE SYSTEM ----------
    private enum PinType { INPUT, OUTPUT, POWER, GROUND }

    private class Pin {
        ICBase owner; int number=-1; PinType type; boolean value=false;
        Circle visual; List<Wire> connections=new ArrayList<>();
        Pin(ICBase owner,PinType type){this.owner=owner;this.type=type;}
        void setValue(boolean v){
            if(this.value==v)return;
            this.value=v;
            if(type==PinType.OUTPUT||type==PinType.POWER||type==PinType.GROUND){
                for(Wire w:new ArrayList<>(connections)){
                    Pin other=w.other(this);
                    if(other.type==PinType.INPUT){
                        other.setValue(v);
                        if(other.owner!=null)other.owner.updateLogic();
                    }
                }
            }
            Platform.runLater(()->externalLEDs.forEach(ExternalLED::refresh));
        }
        void setHighlighted(boolean on){
            if(visual!=null){
                visual.setStroke(on?Color.YELLOW:Color.BLACK);
                visual.setStrokeWidth(on?2.5:1);
            }
        }
    }

    private class Wire {
        Pin a,b; Path path; Color color; boolean selected=false;
        Wire(Pin from,Pin to){
            this.a=from;this.b=to;
            a.connections.add(this); b.connections.add(this);
            color=palette[(paletteIndex++)%palette.length];
            path=new Path();
            path.setStroke(color); path.setStrokeWidth(4);
            path.setStrokeLineCap(StrokeLineCap.ROUND);
            path.setFill(Color.TRANSPARENT);
            path.setEffect(new DropShadow(6,Color.color(0,0,0,0.35)));
            redraw();
            board.getChildren().add(0,path);

           path.setOnMouseClicked(e -> {
    if (e.getButton() == MouseButton.PRIMARY) {
        // Normal selection
        if (connectionStart == null) {
            if (selectedWire != null && selectedWire != this) selectedWire.setSelected(false);
            setSelected(!selected);
            selectedWire = selected ? this : null;
        } else {
            // If user was connecting and clicked a wire, "club" this wire with the selected pin
            connectionStart.setHighlighted(false);
            clubWireWithPin(this, connectionStart);
            connectionStart = null;
        }
        e.consume();
    } else if (e.getButton() == MouseButton.SECONDARY) {
        ContextMenu cm = new ContextMenu();
        MenuItem del = new MenuItem("Delete Wire");
        del.setOnAction(ev -> { remove(); wires.remove(this); evaluateAll(); });
        cm.getItems().add(del);
        cm.show(board, e.getScreenX(), e.getScreenY());
    }
});

            // propagate initial value
            if(a.type==PinType.POWER)b.setValue(true);
            else if(a.type==PinType.GROUND)b.setValue(false);
            else if(a.type==PinType.OUTPUT)b.setValue(a.value);
        }
void redraw() {
    path.getElements().clear();
    Point2D pa = pinSceneCenter(a), pb = pinSceneCenter(b);
    Point2D pA = board.sceneToLocal(pa), pB = board.sceneToLocal(pb);

    double controlOffset = Math.abs(pB.getX() - pA.getX()) * 0.5;

    // Smooth cubic Bezier curve between pins
    path.getElements().add(new MoveTo(pA.getX(), pA.getY()));
    path.getElements().add(new javafx.scene.shape.CubicCurveTo(
        pA.getX() + controlOffset, pA.getY(),
        pB.getX() - controlOffset, pB.getY(),
        pB.getX(), pB.getY()
    ));

    path.setStrokeWidth(selected ? 5 : 3.5);
    path.setStroke(color);
    path.setStrokeLineCap(StrokeLineCap.ROUND);
    path.setFill(Color.TRANSPARENT);
    path.setEffect(new DropShadow(8, color));
}

void setSelected(boolean s) {
    selected = s;
    path.setStroke(s ? Color.YELLOW : color);
    path.setStrokeWidth(s ? 5 : 3.5);
    if (s) path.toFront(); else path.toBack();
}

        void remove(){
            a.connections.remove(this); b.connections.remove(this);
            board.getChildren().remove(path);
            resetDisconnectedLEDs();
        }
        Pin other(Pin p){return p==a?b:a;}
    }

    // resets any LED that lost all connections
    private void resetDisconnectedLEDs(){
        for(ExternalLED led:externalLEDs){
            if(led.pin.connections.isEmpty()){
                led.pin.value=false; led.refresh();
            }
        }
    }

    // ---- External Nodes (Switches, LEDs, Power) ----
    
// ---------- POWER NODE PLACEHOLDER ----------
private class PowerNode {
    Pin pin = new Pin(null, PinType.POWER);
    Circle circle = new Circle(0);
    Group group = new Group();
}

    private class ExternalSwitch{
        Group group=new Group(); Circle node; ToggleButton tb; Pin pin;
        ExternalSwitch(double x,double y,int id){
            node=new Circle(12,Color.DARKRED); node.setStroke(Color.BLACK);
            tb=new ToggleButton("0"); tb.setLayoutX(30); tb.setLayoutY(-10);
            tb.setOnAction(e->{boolean v=tb.isSelected(); tb.setText(v?"1":"0");
                node.setFill(v?Color.LIMEGREEN:Color.DARKRED);
                pin.setValue(v); evaluateAll();});
            pin=new Pin(null,PinType.OUTPUT);
            node.setOnMouseClicked(ev->{if(ev.getButton()==MouseButton.PRIMARY)startConnection(pin);});
            Text lbl=new Text("IN"+id); lbl.setFill(Color.WHITE);
            lbl.setLayoutX(-6); lbl.setLayoutY(28);
            group.getChildren().addAll(node,tb,lbl);
            group.setLayoutX(x); group.setLayoutY(y);
            group.setOnContextMenuRequested(ev->{
                ContextMenu cm=new ContextMenu();
                MenuItem reset=new MenuItem("Reset Switch");
                reset.setOnAction(a->{tb.setSelected(false);tb.setText("0");
                    node.setFill(Color.DARKRED);pin.setValue(false);evaluateAll();});
                cm.getItems().add(reset); cm.show(group,ev.getScreenX(),ev.getScreenY());
            });
        }
    }

    private class ExternalLED{
        Group group=new Group(); Rectangle display; Circle node; Pin pin;
        ExternalLED(double x,double y,int id){
            display=new Rectangle(20,20,Color.DARKRED);
            display.setStroke(Color.BLACK); display.setArcWidth(5); display.setArcHeight(5);
            node=new Circle(10,Color.DARKGRAY); node.setStroke(Color.BLACK);
            node.setCenterX(-30); node.setCenterY(10);
            pin=new Pin(null,PinType.INPUT);
            node.setOnMouseClicked(ev->{if(ev.getButton()==MouseButton.PRIMARY)startConnection(pin);});
            Text lbl=new Text("OUT"+id); lbl.setFill(Color.WHITE);
            lbl.setLayoutX(26); lbl.setLayoutY(14);
            group.getChildren().addAll(display,node,lbl);
            group.setLayoutX(x); group.setLayoutY(y);
            group.setOnContextMenuRequested(ev->{
                ContextMenu cm=new ContextMenu();
                MenuItem remove=new MenuItem("Remove LED");
                remove.setOnAction(a->{
                    undoStack.push(this);
                    board.getChildren().remove(group);
                    externalLEDs.remove(this);
                    for(Wire w:new ArrayList<>(wires)){
                        if(w.a==pin||w.b==pin){w.remove();wires.remove(w);}
                    }
                    evaluateAll();
                });
                cm.getItems().add(remove); cm.show(group,ev.getScreenX(),ev.getScreenY());
            });
        }
        void refresh(){
            display.setFill((powerToggle.isSelected()&&pin.value)?Color.LIMEGREEN:Color.DARKRED);
        }
    }

    private void undoLEDRemoval(){
        if(undoStack.isEmpty())return;
        ExternalLED led=undoStack.pop();
        externalLEDs.add(led);
        board.getChildren().add(led.group);
        evaluateAll();
    }
        // ---------- Toolbar Helpers ----------
 // ---------- Toolbar Helpers ----------
private Timeline powerGlowAnim;

private void stylePowerButton() {
    if (powerGlowAnim != null) powerGlowAnim.stop();

    if (powerToggle.isSelected()) {
        // Power ON visuals
        powerToggle.setStyle("-fx-background-color: linear-gradient(#27ae60,#1e8449); -fx-text-fill: white; -fx-font-weight: bold;");

        // Add glowing drop shadow effect
        DropShadow glow = new DropShadow(25, Color.LIMEGREEN);
        glow.setSpread(0.5);
        powerToggle.setEffect(glow);

        // Create pulsing animation for glow radius
        powerGlowAnim = new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(glow.radiusProperty(), 15)),
            new KeyFrame(Duration.seconds(1.2), new KeyValue(glow.radiusProperty(), 30))
        );
        powerGlowAnim.setAutoReverse(true);
        powerGlowAnim.setCycleCount(Animation.INDEFINITE);
        powerGlowAnim.play();

        powerToggle.setText("POWER ON");
    } else {
        // Power OFF visuals
        powerToggle.setStyle("-fx-background-color: linear-gradient(#e74c3c,#c0392b); -fx-text-fill: white; -fx-font-weight: bold;");
        powerToggle.setEffect(null);
        powerToggle.setText("POWER OFF");
    }
}


    private void placeIC(ICBase ic) {
        ics.add(ic);
        board.getChildren().add(ic.group);
        makeDraggable(ic.group);
       for (ExternalLED led : externalLEDs) {
    makeDraggable(led.group);
}



        evaluateAll();
    }

    // ---------- IC BASE ----------
private abstract class ICBase {
    Group group = new Group();
    Rectangle body;
    Text title;
    Map<Integer, Pin> pins = new HashMap<>();
    Map<Integer, Circle> pinNodes = new HashMap<>();

    ICBase(String name, double x, double y) {
        body = new Rectangle(160, 140, Color.web("#e0e0e0"));
        body.setStroke(Color.web("#222"));
        body.setArcWidth(10);
        body.setArcHeight(10);

        title = new Text(name);
        title.setFont(Font.font("Roboto", 13));
        title.setFill(Color.BLACK);
        title.setX(10);
        title.setY(18);

        group.getChildren().addAll(body, title);
        group.setLayoutX(x);
        group.setLayoutY(y);

        double leftX = -8, rightX = body.getWidth() + 8, top = 28, gap = 16;
// LEFT SIDE PINS (1–7)
for (int i = 1; i <= 7; i++) {
    double py = top + (i - 1) * gap;
    Circle c = new Circle(leftX, py, 6, Color.web("#222"));
    c.setStroke(Color.WHITE);
    group.getChildren().add(c);


    // --- Pin number label ---
    Text pinNum = new Text(String.valueOf(i));
    pinNum.setFont(Font.font("Consolas", 10));
    pinNum.setFill(Color.BLACK);
    pinNum.setLayoutX(leftX - 22); // position to the left
    pinNum.setLayoutY(py + 4);
    group.getChildren().add(pinNum);

    Pin p = new Pin(this, PinType.INPUT);
    p.number = i;
    p.visual = c;
    pins.put(i, p);
    pinNodes.put(i, c);

    // --- GND (pin 7) special handling ---
    if (i == 7) {
        c.setFill(Color.DARKRED);
        c.setStroke(Color.GRAY);
        c.setOnMouseClicked(null);

        Text gndLbl = new Text("GND");
        gndLbl.setFont(Font.font("Consolas", 9));
        gndLbl.setFill(Color.WHITE);
        gndLbl.setLayoutX(leftX - 45); // more left to avoid overlap
        gndLbl.setLayoutY(py -2);     // slightly above
        group.getChildren().add(gndLbl);
        continue;
    }

    // --- Click to connect ---
    c.setOnMouseClicked(ev -> {
        if (ev.getButton() == MouseButton.PRIMARY) startConnection(p);
    });
}
// RIGHT SIDE PINS (8–14)
for (int j = 14; j >= 8; j--) {
    int idx = 14 - j + 1;
    double py = top + (idx - 1) * gap;
    Circle c = new Circle(rightX, py, 6, Color.web("#222"));
    c.setStroke(Color.WHITE);
    group.getChildren().add(c);

    // --- Pin number label ---
    Text pinNum = new Text(String.valueOf(j));
    pinNum.setFont(Font.font("Consolas", 10));
    pinNum.setFill(Color.BLACK);
    pinNum.setLayoutX(rightX + 10); // to the right of pin
    pinNum.setLayoutY(py + 4);
    group.getChildren().add(pinNum);

    Pin p = new Pin(this, PinType.INPUT);
    p.number = j;
    p.visual = c;
    pins.put(j, p);
    pinNodes.put(j, c);

    // --- VCC (pin 14) special handling ---
    if (j == 14) {
        c.setFill(Color.DARKRED);
        c.setStroke(Color.GRAY);
        c.setOnMouseClicked(null);

        Text vccLbl = new Text("VCC");
        vccLbl.setFont(Font.font("Consolas", 9));
        vccLbl.setFill(Color.WHITE);
        vccLbl.setLayoutX(rightX + 18); // further right to avoid overlap
        vccLbl.setLayoutY(py - 2);      // slightly above pin
        group.getChildren().add(vccLbl);
        continue;
    }

    // --- Click to connect ---
    c.setOnMouseClicked(ev -> {
        if (ev.getButton() == MouseButton.PRIMARY) startConnection(p);
    });
}

        // Right-click → remove IC
        group.setOnContextMenuRequested(ev -> {
            ContextMenu cm = new ContextMenu();
            MenuItem remove = new MenuItem("Remove IC");
            remove.setOnAction(a -> {
                for (Wire w : new ArrayList<>(wires)) {
                    if (w.a.owner == this || w.b.owner == this) {
                        w.remove();
                        wires.remove(w);
                    }
                }
                board.getChildren().remove(group);
                ics.remove(this);
                evaluateAll();
            });
            cm.getItems().add(remove);
            cm.show(group, ev.getScreenX(), ev.getScreenY());
        });
    }

    // Set pin type and color
    void setPinType(int pinNumber, PinType type) {
        Pin p = pins.get(pinNumber);
        if (p == null) return;
        p.type = type;
        Circle c = pinNodes.get(pinNumber);
        if (c != null) {
            switch (type) {
                case INPUT -> c.setFill(Color.web("#444"));
                case OUTPUT -> c.setFill(Color.web("#00aaff"));
                case POWER -> c.setFill(Color.RED);
                case GROUND -> c.setFill(Color.BLACK);
            }
        }
    }

    abstract void updateLogic();

    boolean isPowered() {
        return powerToggle.isSelected(); // All ICs powered when Power ON
    }
}


    // ---------- IC IMPLEMENTATIONS ----------
    private class IC7400 extends ICBase {
        IC7400(double x, double y) {
            super("7400 NAND", x, y);
            setPinType(7, PinType.GROUND); setPinType(14, PinType.POWER);
            setPinType(1, PinType.INPUT); setPinType(2, PinType.INPUT); setPinType(3, PinType.OUTPUT);
            setPinType(4, PinType.INPUT); setPinType(5, PinType.INPUT); setPinType(6, PinType.OUTPUT);
            setPinType(9, PinType.INPUT); setPinType(10, PinType.INPUT); setPinType(8, PinType.OUTPUT);
            setPinType(12, PinType.INPUT); setPinType(13, PinType.INPUT); setPinType(11, PinType.OUTPUT);
        }
        void updateLogic() {
            if (!isPowered()) { for (int p : new int[]{3, 6, 8, 11}) pins.get(p).setValue(false); return; }
            pins.get(3).setValue(!(pins.get(1).value && pins.get(2).value));
            pins.get(6).setValue(!(pins.get(4).value && pins.get(5).value));
            pins.get(8).setValue(!(pins.get(9).value && pins.get(10).value));
            pins.get(11).setValue(!(pins.get(12).value && pins.get(13).value));
        }
    }

    private class IC7402 extends ICBase {
        IC7402(double x, double y) {
            super("7402 NOR", x, y);
            setPinType(7, PinType.GROUND); setPinType(14, PinType.POWER);
            setPinType(1, PinType.OUTPUT); setPinType(2, PinType.INPUT); setPinType(3, PinType.INPUT);
            setPinType(4, PinType.OUTPUT); setPinType(5, PinType.INPUT); setPinType(6, PinType.INPUT);
            setPinType(8, PinType.INPUT); setPinType(9, PinType.INPUT); setPinType(10, PinType.OUTPUT);
            setPinType(11, PinType.INPUT); setPinType(12, PinType.INPUT); setPinType(13, PinType.OUTPUT);
        }
        void updateLogic() {
            if (!isPowered()) { for (int p : new int[]{1, 4, 10, 13}) pins.get(p).setValue(false); return; }
            pins.get(1).setValue(!(pins.get(2).value || pins.get(3).value));
            pins.get(4).setValue(!(pins.get(5).value || pins.get(6).value));
            pins.get(10).setValue(!(pins.get(8).value || pins.get(9).value));
            pins.get(13).setValue(!(pins.get(11).value || pins.get(12).value));
        }
    }

    private class IC7408 extends ICBase {
        IC7408(double x, double y) {
            super("7408 AND", x, y);
            setPinType(7, PinType.GROUND); setPinType(14, PinType.POWER);
            setPinType(1, PinType.INPUT); setPinType(2, PinType.INPUT); setPinType(3, PinType.OUTPUT);
            setPinType(4, PinType.INPUT); setPinType(5, PinType.INPUT); setPinType(6, PinType.OUTPUT);
            setPinType(9, PinType.INPUT); setPinType(10, PinType.INPUT); setPinType(8, PinType.OUTPUT);
            setPinType(12, PinType.INPUT); setPinType(13, PinType.INPUT); setPinType(11, PinType.OUTPUT);
        }
        void updateLogic() {
            if (!isPowered()) { for (int p : new int[]{3, 6, 8, 11}) pins.get(p).setValue(false); return; }
            pins.get(3).setValue(pins.get(1).value && pins.get(2).value);
            pins.get(6).setValue(pins.get(4).value && pins.get(5).value);
            pins.get(8).setValue(pins.get(9).value && pins.get(10).value);
            pins.get(11).setValue(pins.get(12).value && pins.get(13).value);
        }
    }

    private class IC7432 extends ICBase {
        IC7432(double x, double y) {
            super("7432 OR", x, y);
            setPinType(7, PinType.GROUND); setPinType(14, PinType.POWER);
            setPinType(1, PinType.INPUT); setPinType(2, PinType.INPUT); setPinType(3, PinType.OUTPUT);
            setPinType(4, PinType.INPUT); setPinType(5, PinType.INPUT); setPinType(6, PinType.OUTPUT);
            setPinType(9, PinType.INPUT); setPinType(10, PinType.INPUT); setPinType(8, PinType.OUTPUT);
            setPinType(12, PinType.INPUT); setPinType(13, PinType.INPUT); setPinType(11, PinType.OUTPUT);
        }
        void updateLogic() {
            if (!isPowered()) { for (int p : new int[]{3, 6, 8, 11}) pins.get(p).setValue(false); return; }
            pins.get(3).setValue(pins.get(1).value || pins.get(2).value);
            pins.get(6).setValue(pins.get(4).value || pins.get(5).value);
            pins.get(8).setValue(pins.get(9).value || pins.get(10).value);
            pins.get(11).setValue(pins.get(12).value || pins.get(13).value);
        }
    }

    private class IC7404 extends ICBase {
        IC7404(double x, double y) {
            super("7404 NOT", x, y);
            setPinType(7, PinType.GROUND); setPinType(14, PinType.POWER);
            int[][] map = {{1, 2}, {3, 4}, {5, 6}, {8, 9}, {10, 11}, {12, 13}};
            for (int[] m : map) { setPinType(m[0], PinType.INPUT); setPinType(m[1], PinType.OUTPUT); }
        }
        void updateLogic() {
            if (!isPowered()) { for (int[] p : new int[][]{{2}, {4}, {6}, {9}, {11}, {13}}) pins.get(p[0]).setValue(false); return; }
            pins.get(2).setValue(!pins.get(1).value);
            pins.get(4).setValue(!pins.get(3).value);
            pins.get(6).setValue(!pins.get(5).value);
            pins.get(9).setValue(!pins.get(8).value);
            pins.get(11).setValue(!pins.get(10).value);
            pins.get(13).setValue(!pins.get(12).value);
        }
    }

    private class IC7486 extends ICBase {
        IC7486(double x, double y) {
            super("7486 XOR", x, y);
            setPinType(7, PinType.GROUND); setPinType(14, PinType.POWER);
            int[][] map = {{1, 2, 3}, {4, 5, 6}, {9, 10, 8}, {12, 13, 11}};
            for (int[] m : map) { setPinType(m[0], PinType.INPUT); setPinType(m[1], PinType.INPUT); setPinType(m[2], PinType.OUTPUT); }
        }
        void updateLogic() {
            if (!isPowered()) { for (int p : new int[]{3, 6, 8, 11}) pins.get(p).setValue(false); return; }
            pins.get(3).setValue(pins.get(1).value ^ pins.get(2).value);
            pins.get(6).setValue(pins.get(4).value ^ pins.get(5).value);
            pins.get(8).setValue(pins.get(9).value ^ pins.get(10).value);
            pins.get(11).setValue(pins.get(12).value ^ pins.get(13).value);
        }
    }

    private class IC7487 extends ICBase {
        IC7487(double x, double y) {
            super("7487 XNOR", x, y);
            setPinType(7, PinType.GROUND); setPinType(14, PinType.POWER);
            int[][] map = {{1, 2, 3}, {4, 5, 6}, {9, 10, 8}, {12, 13, 11}};
            for (int[] m : map) { setPinType(m[0], PinType.INPUT); setPinType(m[1], PinType.INPUT); setPinType(m[2], PinType.OUTPUT); }
        }
        void updateLogic() {
            if (!isPowered()) { for (int p : new int[]{3, 6, 8, 11}) pins.get(p).setValue(false); return; }
            pins.get(3).setValue(!(pins.get(1).value ^ pins.get(2).value));
            pins.get(6).setValue(!(pins.get(4).value ^ pins.get(5).value));
            pins.get(8).setValue(!(pins.get(9).value ^ pins.get(10).value));
            pins.get(11).setValue(!(pins.get(12).value ^ pins.get(13).value));
        }
    }
    // --- 3-Input AND IC ---
private class IC_AND3 extends ICBase {
    IC_AND3(double x, double y) {
        super("3-Input AND", x, y);
        setPinType(1, PinType.INPUT); // A
        setPinType(2, PinType.INPUT); // B
        setPinType(3, PinType.INPUT); // C
        setPinType(4, PinType.OUTPUT); // Y
        setPinType(14, PinType.POWER);
        setPinType(7, PinType.GROUND);
    }

    @Override
    void updateLogic() {
        if (!isPowered()) { pins.get(4).setValue(false); return; }
        pins.get(4).setValue(pins.get(1).value && pins.get(2).value && pins.get(3).value);
    }
}
// --- 3-Input OR IC ---
private class IC_OR3 extends ICBase {
    IC_OR3(double x, double y) {
        super("3-Input OR", x, y);
        setPinType(1, PinType.INPUT);
        setPinType(2, PinType.INPUT);
        setPinType(3, PinType.INPUT);
        setPinType(4, PinType.OUTPUT);
        setPinType(14, PinType.POWER);
        setPinType(7, PinType.GROUND);
    }

    @Override
    void updateLogic() {
        if (!isPowered()) { pins.get(4).setValue(false); return; }
        pins.get(4).setValue(pins.get(1).value || pins.get(2).value || pins.get(3).value);
    }
}
// --- 3-Input NAND IC ---
private class IC_NAND3 extends ICBase {
    IC_NAND3(double x, double y) {
        super("3-Input NAND", x, y);
        setPinType(1, PinType.INPUT);
        setPinType(2, PinType.INPUT);
        setPinType(3, PinType.INPUT);
        setPinType(4, PinType.OUTPUT);
        setPinType(14, PinType.POWER);
        setPinType(7, PinType.GROUND);
    }

    @Override
    void updateLogic() {
        if (!isPowered()) { pins.get(4).setValue(false); return; }
        pins.get(4).setValue(!(pins.get(1).value && pins.get(2).value && pins.get(3).value));
    }
}
// --- 3-Input NOR IC ---
private class IC_NOR3 extends ICBase {
    IC_NOR3(double x, double y) {
        super("3-Input NOR", x, y);
        setPinType(1, PinType.INPUT);
        setPinType(2, PinType.INPUT);
        setPinType(3, PinType.INPUT);
        setPinType(4, PinType.OUTPUT);
        setPinType(14, PinType.POWER);
        setPinType(7, PinType.GROUND);
    }

    @Override
    void updateLogic() {
        if (!isPowered()) { pins.get(4).setValue(false); return; }
        pins.get(4).setValue(!(pins.get(1).value || pins.get(2).value || pins.get(3).value));
    }
}
// --- 3-Input XOR IC ---
private class IC_XOR3 extends ICBase {
    IC_XOR3(double x, double y) {
        super("3-Input XOR", x, y);
        setPinType(1, PinType.INPUT);
        setPinType(2, PinType.INPUT);
        setPinType(3, PinType.INPUT);
        setPinType(4, PinType.OUTPUT);
        setPinType(14, PinType.POWER);
        setPinType(7, PinType.GROUND);
    }

    @Override
    void updateLogic() {
        if (!isPowered()) { pins.get(4).setValue(false); return; }
        int sum = (pins.get(1).value ? 1 : 0) + (pins.get(2).value ? 1 : 0) + (pins.get(3).value ? 1 : 0);
        pins.get(4).setValue(sum % 2 == 1); // output 1 if odd number of 1s
    }
}
// --- 3-Input XNOR IC ---
private class IC_XNOR3 extends ICBase {
    IC_XNOR3(double x, double y) {
        super("3-Input XNOR", x, y);
        setPinType(1, PinType.INPUT);
        setPinType(2, PinType.INPUT);
        setPinType(3, PinType.INPUT);
        setPinType(4, PinType.OUTPUT);
        setPinType(14, PinType.POWER);
        setPinType(7, PinType.GROUND);
    }

    @Override
    void updateLogic() {
        if (!isPowered()) { pins.get(4).setValue(false); return; }
        int sum = (pins.get(1).value ? 1 : 0) + (pins.get(2).value ? 1 : 0) + (pins.get(3).value ? 1 : 0);
        pins.get(4).setValue(sum % 2 == 0); // even number of 1s → output 1
    }
}

// ===========================================================
// ✅ COMBINATIONAL CIRCUITS (Final Corrected Implementations)
// ===========================================================

// --- 2x1 MUX ---
private class IC_MUX2x1 extends ICBase {
    IC_MUX2x1(double x, double y) {
        super("2x1 MUX", x, y);
        setPinType(1, PinType.INPUT);  // I0
        setPinType(2, PinType.INPUT);  // I1
        setPinType(3, PinType.INPUT);  // S (Select)
        setPinType(4, PinType.OUTPUT); // Y
        setPinType(7, PinType.GROUND);
        setPinType(14, PinType.POWER);
    }

    @Override
    void updateLogic() {
        if (!isPowered()) { pins.get(4).setValue(false); return; }
        boolean s = pins.get(3).value;
        pins.get(4).setValue(s ? pins.get(2).value : pins.get(1).value);
    }
}

// --- 4x1 MUX ---
private class IC_MUX4x1 extends ICBase {
    IC_MUX4x1(double x, double y) {
        super("4x1 MUX", x, y);
        setPinType(1, PinType.INPUT); // I0
        setPinType(2, PinType.INPUT); // I1
        setPinType(3, PinType.INPUT); // I2
        setPinType(4, PinType.INPUT); // I3
        setPinType(5, PinType.INPUT); // S0
        setPinType(6, PinType.INPUT); // S1
        setPinType(11, PinType.OUTPUT); // Y
        setPinType(7, PinType.GROUND);
        setPinType(14, PinType.POWER);
    }

    @Override
    void updateLogic() {
        if (!isPowered()) { pins.get(11).setValue(false); return; }
        int sel = (pins.get(6).value ? 2 : 0) + (pins.get(5).value ? 1 : 0);
        boolean[] I = { pins.get(1).value, pins.get(2).value, pins.get(3).value, pins.get(4).value };
        pins.get(11).setValue(I[sel]);
    }
}

// --- 8x1 MUX ---
private class IC_MUX8x1 extends ICBase {
    IC_MUX8x1(double x, double y) {
        super("8x1 MUX", x, y);
        for (int i = 1; i <= 8; i++) setPinType(i, PinType.INPUT); // I0–I7
        setPinType(9, PinType.INPUT);  // S0
        setPinType(10, PinType.INPUT); // S1
        setPinType(11, PinType.INPUT); // S2
        setPinType(12, PinType.OUTPUT); // Y
        setPinType(7, PinType.GROUND);
        setPinType(14, PinType.POWER);
    }

    @Override
    void updateLogic() {
        if (!isPowered()) { pins.get(12).setValue(false); return; }
        int sel = (pins.get(11).value ? 4 : 0) + (pins.get(10).value ? 2 : 0) + (pins.get(9).value ? 1 : 0);
        pins.get(12).setValue(pins.get(sel + 1).value);
    }
}

// --- 1x2 DEMUX ---
private class IC_DEMUX1x2 extends ICBase {
    IC_DEMUX1x2(double x, double y) {
        super("1x2 DEMUX", x, y);
        setPinType(1, PinType.INPUT);  // D (Data)
        setPinType(2, PinType.INPUT);  // S (Select)
        setPinType(3, PinType.OUTPUT); // Y0
        setPinType(4, PinType.OUTPUT); // Y1
        setPinType(7, PinType.GROUND);
        setPinType(14, PinType.POWER);
    }

    @Override
    void updateLogic() {
        if (!isPowered()) {
            pins.get(3).setValue(false);
            pins.get(4).setValue(false);
            return;
        }
        boolean D = pins.get(1).value;
        boolean S = pins.get(2).value;
        pins.get(3).setValue(D && !S);
        pins.get(4).setValue(D && S);
    }
}

// --- 1x4 DEMUX ---
private class IC_DEMUX1x4 extends ICBase {
    IC_DEMUX1x4(double x, double y) {
        super("1x4 DEMUX", x, y);
        setPinType(1, PinType.INPUT);  // D (Data)
        setPinType(2, PinType.INPUT);  // S0
        setPinType(3, PinType.INPUT);  // S1
        setPinType(4, PinType.OUTPUT); // Y0
        setPinType(5, PinType.OUTPUT); // Y1
        setPinType(6, PinType.OUTPUT); // Y2
        setPinType(8, PinType.OUTPUT); // Y3
        setPinType(7, PinType.GROUND);
        setPinType(14, PinType.POWER);
    }

    @Override
    void updateLogic() {
        if (!isPowered()) { for (int p : new int[]{4, 5, 6, 8}) pins.get(p).setValue(false); return; }
        boolean D = pins.get(1).value;
        int sel = (pins.get(3).value ? 2 : 0) + (pins.get(2).value ? 1 : 0);
        pins.get(4).setValue(D && sel == 0);
        pins.get(5).setValue(D && sel == 1);
        pins.get(6).setValue(D && sel == 2);
        pins.get(8).setValue(D && sel == 3);
    }
}

// --- 1x8 DEMUX ---
private class IC_DEMUX1x8 extends ICBase {
    IC_DEMUX1x8(double x, double y) {
        super("1x8 DEMUX", x, y);
        setPinType(1, PinType.INPUT);  // D
        setPinType(2, PinType.INPUT);  // S0
        setPinType(3, PinType.INPUT);  // S1
        setPinType(4, PinType.INPUT);  // S2
        for (int i = 5; i <= 12; i++) setPinType(i, PinType.OUTPUT); // Y0–Y7
        setPinType(7, PinType.GROUND);
        setPinType(14, PinType.POWER);
    }

    @Override
    void updateLogic() {
        if (!isPowered()) { for (int i = 5; i <= 12; i++) pins.get(i).setValue(false); return; }
        boolean D = pins.get(1).value;
        int sel = (pins.get(4).value ? 4 : 0) + (pins.get(3).value ? 2 : 0) + (pins.get(2).value ? 1 : 0);
        for (int i = 0; i < 8; i++) pins.get(i + 5).setValue(D && i == sel);
    }
}

// --- 4x2 ENCODER ---
private class IC_ENC4x2 extends ICBase {
    IC_ENC4x2(double x, double y) {
        super("4x2 ENCODER", x, y);
        for (int i = 1; i <= 4; i++) setPinType(i, PinType.INPUT); // D0–D3
        setPinType(5, PinType.OUTPUT); // Y0
        setPinType(6, PinType.OUTPUT); // Y1
        setPinType(7, PinType.GROUND);
        setPinType(14, PinType.POWER);
    }

    @Override
    void updateLogic() {
        if (!isPowered()) { pins.get(5).setValue(false); pins.get(6).setValue(false); return; }

        int code = -1;
        for (int i = 0; i < 4; i++) if (pins.get(i + 1).value) code = i;
        if (code == -1) { pins.get(5).setValue(false); pins.get(6).setValue(false); return; }

        pins.get(5).setValue((code & 1) != 0); // Y0
        pins.get(6).setValue((code & 2) != 0); // Y1
    }
}

// --- 2x4 DECODER (✅ WITH ENABLE) ---
private class IC_DEC2x4 extends ICBase {
    IC_DEC2x4(double x, double y) {
        super("2x4 DECODER", x, y);
        setPinType(1, PinType.INPUT);  // A
        setPinType(2, PinType.INPUT);  // B
        setPinType(3, PinType.INPUT);  // EN
        setPinType(4, PinType.OUTPUT); // Y0
        setPinType(5, PinType.OUTPUT); // Y1
        setPinType(6, PinType.OUTPUT); // Y2
        setPinType(8, PinType.OUTPUT); // Y3
        setPinType(7, PinType.GROUND);
        setPinType(14, PinType.POWER);
    }

    @Override
    void updateLogic() {
        if (!isPowered()) { for (int p : new int[]{4, 5, 6, 8}) pins.get(p).setValue(false); return; }
        boolean en = pins.get(3).value;
        if (!en) { for (int p : new int[]{4, 5, 6, 8}) pins.get(p).setValue(false); return; }

        int sel = (pins.get(2).value ? 2 : 0) + (pins.get(1).value ? 1 : 0);
        pins.get(4).setValue(sel == 0);
        pins.get(5).setValue(sel == 1);
        pins.get(6).setValue(sel == 2);
        pins.get(8).setValue(sel == 3);
    }
}
// =====================================
// 🔧 Helper for Toolbar Dropdown IC Menus
// =====================================

private MenuItem createICMenuItem(String name) {
    MenuItem item = new MenuItem(name);
    item.setOnAction(e -> addICToBoard(name));
    return item;
}

private void addICToBoard(String name) {
    switch (name) {
        // --- Logic Gates ---
        case "7400 NAND" -> placeIC(new IC7400(200, 120));
        case "7402 NOR" -> placeIC(new IC7402(420, 120));
        case "7408 AND" -> placeIC(new IC7408(200, 300));
        case "7432 OR" -> placeIC(new IC7432(420, 300));
        case "7404 NOT" -> placeIC(new IC7404(640, 120));
        case "7486 XOR" -> placeIC(new IC7486(640, 300));
        case "7487 XNOR" -> placeIC(new IC7487(860, 200));

        // --- 3-Input Gates ---
        case "AND3" -> placeIC(new IC_AND3(200, 500));
        case "OR3" -> placeIC(new IC_OR3(400, 500));
        case "NAND3" -> placeIC(new IC_NAND3(600, 500));
        case "NOR3" -> placeIC(new IC_NOR3(800, 500));
        case "XOR3" -> placeIC(new IC_XOR3(1000, 500));
        case "XNOR3" -> placeIC(new IC_XNOR3(1200, 500));

        // --- Combinational Circuits ---
        case "2x1 MUX" -> placeIC(new IC_MUX2x1(200, 600));
        case "4x1 MUX" -> placeIC(new IC_MUX4x1(400, 600));
        case "8x1 MUX" -> placeIC(new IC_MUX8x1(600, 600));
        case "1x2 DEMUX" -> placeIC(new IC_DEMUX1x2(800, 600));
        case "1x4 DEMUX" -> placeIC(new IC_DEMUX1x4(1000, 600));
        case "1x8 DEMUX" -> placeIC(new IC_DEMUX1x8(1200, 600));
        case "4x2 ENCODER" -> placeIC(new IC_ENC4x2(800, 700));
        case "2x4 DECODER" -> placeIC(new IC_DEC2x4(1000, 700));
    }
}


// ---------- CLOCK PULSE GENERATOR ----------
private class ClockPulse {
    Group group = new Group();
    Circle node;
    Pin outputPin;
    ToggleButton startBtn;
    Timeline pulse;
    boolean state = false;

    ClockPulse(double x, double y) {
        node = new Circle(15, Color.DARKRED);
        node.setStroke(Color.BLACK);
        outputPin = new Pin(null, PinType.OUTPUT);
        node.setOnMouseClicked(ev -> {
            if (ev.getButton() == MouseButton.PRIMARY) startConnection(outputPin);
        });

        startBtn = new ToggleButton("Start");
        startBtn.setLayoutX(40);
        startBtn.setLayoutY(-12);
        startBtn.setOnAction(e -> togglePulse());

        Label lbl = new Label("CLK");
        lbl.setTextFill(Color.WHITE);
        lbl.setLayoutX(-8);
        lbl.setLayoutY(25);

        group.getChildren().addAll(node, startBtn, lbl);
        group.setLayoutX(x);
        group.setLayoutY(y);
    }

    private void togglePulse() {
        if (startBtn.isSelected()) {
            startBtn.setText("Stop");
            pulse = new Timeline(new KeyFrame(Duration.seconds(0.5), ev -> toggleClock()));
            pulse.setCycleCount(Animation.INDEFINITE);
            pulse.play();
        } else {
            startBtn.setText("Start");
            if (pulse != null) pulse.stop();
            node.setFill(Color.DARKRED);
            outputPin.setValue(false);
            evaluateAll();
        }
    }

    private void toggleClock() {
        state = !state;
        node.setFill(state ? Color.LIMEGREEN : Color.DARKRED);
        outputPin.setValue(state);
        evaluateAll();
    }
}




    // ---------- CONNECTION ----------
    private void startConnection(Pin p) {
        if (connectionStart == null) {
            connectionStart = p;
            p.setHighlighted(true);
        } else {
            connectionStart.setHighlighted(false);
            attemptConnect(connectionStart, p);
            connectionStart = null;
        }
    }

    private void attemptConnect(Pin a, Pin b) {
    if (a == b) return;

    // Allow OUTPUT → INPUT in both directions
    if ((a.type == PinType.OUTPUT && b.type == PinType.INPUT) ||
        (b.type == PinType.OUTPUT && a.type == PinType.INPUT)) {
        createWire(a.type == PinType.OUTPUT ? a : b, a.type == PinType.INPUT ? a : b);
        evaluateAll(); // update immediately after connection
        return;
    }

    // Allow OUTPUT → LED INPUT (direct)
    if ((a.type == PinType.OUTPUT && b.owner == null && b.type == PinType.INPUT) ||
        (b.type == PinType.OUTPUT && a.owner == null && a.type == PinType.INPUT)) {
        createWire(a.type == PinType.OUTPUT ? a : b, a.type == PinType.INPUT ? a : b);
        evaluateAll();
        return;
    }

    // Allow chaining from external switch OUTPUT → IC INPUT
    if ((a.owner == null && a.type == PinType.OUTPUT && b.owner != null && b.type == PinType.INPUT) ||
        (b.owner == null && b.type == PinType.OUTPUT && a.owner != null && a.type == PinType.INPUT)) {
        createWire(a.type == PinType.OUTPUT ? a : b, a.type == PinType.INPUT ? a : b);
        evaluateAll();
        return;
    }

    // Block invalid connections
    if ((a.type == PinType.OUTPUT && b.type == PinType.OUTPUT) ||
        (a.type == PinType.INPUT && b.type == PinType.INPUT)) {
        new Alert(Alert.AlertType.ERROR,
            "❌ Invalid connection: You can only connect OUTPUT to INPUT!",
            ButtonType.OK).showAndWait();
        return;
    }

    new Alert(Alert.AlertType.WARNING,
        "⚠ Allowed connections: OUTPUT → INPUT (including IC chaining)",
        ButtonType.OK).showAndWait();
}

    private void clubWireWithPin(Wire existingWire, Pin newPin) {
    Pin endA = existingWire.a;
    Pin endB = existingWire.b;

    // If connecting an input pin
    if (newPin.type == PinType.INPUT) {
        if (endA.type == PinType.OUTPUT) createWire(endA, newPin);
        else if (endB.type == PinType.OUTPUT) createWire(endB, newPin);
        else createWire(endA, newPin); // safe default
    }
    // If connecting an output pin
    else if (newPin.type == PinType.OUTPUT) {
        if (endA.type == PinType.INPUT) createWire(newPin, endA);
        if (endB.type == PinType.INPUT) createWire(newPin, endB);
    } else {
        Alert al = new Alert(Alert.AlertType.ERROR, "Invalid wire clubbing!", ButtonType.OK);
        al.showAndWait();
        flashWire(existingWire);
        return;
    }

    evaluateAll();
}
private void flashWire(Wire w) {
    Color original = (Color) w.path.getStroke();
    Timeline t = new Timeline(
        new KeyFrame(Duration.ZERO, new KeyValue(w.path.strokeProperty(), Color.YELLOW)),
        new KeyFrame(Duration.seconds(0.4), new KeyValue(w.path.strokeProperty(), original))
    );
    t.play();
}
/** Create parallel wires between two lists of pins.
 *  fromPins.size() must equal toPins.size().
 *  Will check output/input validity for each pair and create wires.
 */
private void createParallelWires(List<Pin> fromPins, List<Pin> toPins) {
    if (fromPins.size() != toPins.size()) {
        new Alert(Alert.AlertType.ERROR, "Bus size mismatch").showAndWait();
        return;
    }

    for (int i = 0; i < fromPins.size(); i++) {
        Pin a = fromPins.get(i);
        Pin b = toPins.get(i);

        // Check invalid pair
        if ((a.type == PinType.OUTPUT && b.type == PinType.OUTPUT) ||
            (a.type == PinType.INPUT && b.type == PinType.INPUT)) {
            new Alert(Alert.AlertType.ERROR, "Invalid bus connection at bit " + i + 
                ": cannot connect OUTPUT↔OUTPUT or INPUT↔INPUT").showAndWait();
            // Optionally skip or abort entire bus creation. Here we abort.
            return;
        }

        // If multiple outputs would drive same net, detect and block:
        Pin driving = (a.type == PinType.OUTPUT) ? a : (b.type == PinType.OUTPUT ? b : null);
        Pin other = (driving == a) ? b : a;
        if (driving != null) {
            // check if target already has some output connected (naive check):
            for (Wire w : wires) {
                Pin p1 = w.a, p2 = w.b;
                // if other side of an existing wire is an OUTPUT and not the same as `driving`
                if ((p1 == other && p2.type == PinType.OUTPUT && p2 != driving) ||
                    (p2 == other && p1.type == PinType.OUTPUT && p1 != driving)) {
                    new Alert(Alert.AlertType.ERROR, "Bus conflict at bit " + i + 
                        ": another output already drives this net.").showAndWait();
                    return;
                }
            }
        }

        // All good — create wire in usual manner:
        createWire(a.type == PinType.OUTPUT ? a : b, a.type == PinType.INPUT ? a : b);
    }

    // Redraw and evaluate
    for (Wire w : wires) w.redraw();
    evaluateAll();
}


    private void createWire(Pin from, Pin to) {
        Wire w = new Wire(from, to);
        wires.add(w);
        for (Wire ww : wires) ww.redraw();
        evaluateAll();
    }

    // ---------- TRUTH TABLE ----------
    private boolean isSimulating = false;
private Timeline simulationTimeline;
// ---------- AUTO TRUTH TABLE HELPER (for MUX, DEMUX, Encoder, Decoder) ----------
// ---------- TRUTH TABLE (Combined with auto-generation for ICs) ----------
private void showTruthTable() {
    // If an IC is selected, try auto truth table
    ICBase ic = null;
    if (!ics.isEmpty()) {
        ic = ics.get(ics.size() - 1); // last placed IC assumed as target
    }

    if (ic != null) {
        List<String> autoHeaders = new ArrayList<>();
        List<String[]> autoRows = generateTruthTableForIC(ic.title.getText(), autoHeaders);

        if (autoRows != null) {
            populateTruthTable(autoHeaders, autoRows);
            return;
        }
    }

    // Otherwise, use external connections (switches and LEDs)
    List<ExternalSwitch> activeInputs = externalSwitches.stream()
        .filter(sw -> !sw.pin.connections.isEmpty())
        .collect(Collectors.toList());
    List<ExternalLED> activeOutputs = externalLEDs.stream()
        .filter(led -> !led.pin.connections.isEmpty())
        .collect(Collectors.toList());

    if (activeInputs.isEmpty() || activeOutputs.isEmpty()) {
        new Alert(Alert.AlertType.WARNING, "⚠ Connect at least one input and one output first!").showAndWait();
        return;
    }

    int nInputs = activeInputs.size();
    int combos = 1 << nInputs;

    TableView<Map<String, String>> table = new TableView<>();
    table.setPrefHeight(400);
    table.setPrefWidth(600);

    // Columns for inputs
    for (ExternalSwitch sw : activeInputs) {
        String label = ((Text) sw.group.getChildren().stream()
            .filter(n -> n instanceof Text)
            .findFirst().orElse(new Text("IN"))).getText();

        TableColumn<Map<String, String>, String> col = new TableColumn<>(label);
        col.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().get(label)));
        table.getColumns().add(col);
    }

    // Columns for outputs
    for (ExternalLED led : activeOutputs) {
        String label = ((Text) led.group.getChildren().stream()
            .filter(n -> n instanceof Text)
            .findFirst().orElse(new Text("OUT"))).getText();

        TableColumn<Map<String, String>, String> col = new TableColumn<>(label);
        col.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().get(label)));
        table.getColumns().add(col);
    }

    // Status column
    TableColumn<Map<String, String>, String> statusCol = new TableColumn<>("Status");
    statusCol.setCellValueFactory(data ->
        new javafx.beans.property.SimpleStringProperty(data.getValue().getOrDefault("Status", ""))
    );
    table.getColumns().add(statusCol);

    // Prepare combinations
    List<Map<String, String>> rows = new ArrayList<>();
    for (int mask = 0; mask < combos; mask++) {
        Map<String, String> row = new LinkedHashMap<>();
        for (int i = 0; i < nInputs; i++) {
            boolean value = ((mask >> i) & 1) == 1;
            row.put("IN" + (i + 1), value ? "1" : "0");
        }
        for (int j = 0; j < activeOutputs.size(); j++) {
            row.put("OUT" + (j + 1), "-");
        }
        row.put("Status", "");
        rows.add(row);
    }
    table.getItems().addAll(rows);

    // Dialog setup
    Dialog<Void> dialog = new Dialog<>();
    dialog.setTitle("Truth Table Simulation");
    dialog.setResizable(true);

    VBox root = new VBox(10);
    root.setPadding(new Insets(10));

    Label summaryLabel = new Label("Simulation Summary: Pending...");
    summaryLabel.setTextFill(Color.WHITE);

    Button simulateBtn = new Button("Simulate Circuit");
    simulateBtn.setTextFill(Color.WHITE);
    simulateBtn.setStyle("-fx-background-color: #27ae60; -fx-font-weight: bold;");
    simulateBtn.setPrefWidth(160);

    root.getChildren().addAll(table, simulateBtn, summaryLabel);
    root.setAlignment(Pos.CENTER);
    dialog.getDialogPane().setContent(root);
    dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

    // Simulation logic
    simulateBtn.setOnAction(e -> {
        if (simulateBtn.getText().equals("Stop Simulation")) {
            simulateBtn.setText("Simulate Circuit");
            simulateBtn.setStyle("-fx-background-color: #27ae60; -fx-font-weight: bold;");
            if (simulationTimeline != null) simulationTimeline.stop();
            summaryLabel.setText("Simulation Stopped.");
            return;
        }

        simulateBtn.setText("Stop Simulation");
        simulateBtn.setStyle("-fx-background-color: #e67e22; -fx-font-weight: bold;");
        int[] index = {0};
        int[] pass = {0};
        int[] fail = {0};

        simulationTimeline = new Timeline(new KeyFrame(Duration.seconds(0.8), ev -> {
            if (index[0] >= rows.size()) {
                simulateBtn.setText("Simulate Circuit");
                simulateBtn.setStyle("-fx-background-color: #27ae60; -fx-font-weight: bold;");
                simulationTimeline.stop();
                summaryLabel.setText("Simulation Complete — ✅ " + pass[0] + "  ❌ " + fail[0]);
                return;
            }

            Map<String, String> row = rows.get(index[0]);
            for (int i = 0; i < nInputs; i++) {
                boolean val = row.get("IN" + (i + 1)).equals("1");
                activeInputs.get(i).tb.setSelected(val);
                activeInputs.get(i).pin.setValue(val);
                activeInputs.get(i).node.setFill(val ? Color.LIMEGREEN : Color.DARKRED);
            }

            evaluateAll();

            boolean match = true;
            for (int j = 0; j < activeOutputs.size(); j++) {
                boolean outVal = activeOutputs.get(j).pin.value;
                row.put("OUT" + (j + 1), outVal ? "1" : "0");
            }

            row.put("Status", match ? "✅" : "❌");
            if (match) pass[0]++; else fail[0]++;
            table.refresh();
            index[0]++;
        }));

        simulationTimeline.setCycleCount(Animation.INDEFINITE);
        simulationTimeline.play();
    });

    dialog.showAndWait();
}
// ---------- AUTO TRUTH TABLE GENERATOR ----------
private List<String[]> generateTruthTableForIC(String icName, List<String> headers) {
    headers.clear();

    switch (icName.toUpperCase()) {
        // ---------------- MUX FAMILY ----------------
        case "2X1 MUX" -> {
            headers.addAll(List.of("A", "B", "S", "Y"));
            List<String[]> rows = new ArrayList<>();
            for (int A = 0; A <= 1; A++)
                for (int B = 0; B <= 1; B++)
                    for (int S = 0; S <= 1; S++) {
                        int Y = (S == 1) ? B : A;
                        rows.add(new String[]{String.valueOf(A), String.valueOf(B),
                                String.valueOf(S), String.valueOf(Y)});
                    }
            return rows;
        }

        case "4X1 MUX" -> {
            headers.addAll(List.of("I0", "I1", "I2", "I3", "S0", "S1", "Y"));
            List<String[]> rows = new ArrayList<>();
            for (int i0 = 0; i0 <= 1; i0++)
                for (int i1 = 0; i1 <= 1; i1++)
                    for (int i2 = 0; i2 <= 1; i2++)
                        for (int i3 = 0; i3 <= 1; i3++)
                            for (int s0 = 0; s0 <= 1; s0++)
                                for (int s1 = 0; s1 <= 1; s1++) {
                                    int sel = (s1 << 1) | s0;
                                    int[] inputs = {i0, i1, i2, i3};
                                    int y = inputs[sel];
                                    rows.add(new String[]{""+i0,""+i1,""+i2,""+i3,""+s0,""+s1,""+y});
                                }
            return rows;
        }

        case "8X1 MUX" -> {
            headers.addAll(List.of("I0","I1","I2","I3","I4","I5","I6","I7","S0","S1","S2","Y"));
            List<String[]> rows = new ArrayList<>();
            for (int[] vals = new int[11]; vals[0] <= 1; ) {
                // generate 11-bit binary count
                for (int i = 0; i < 11; i++) vals[i]++;
                break;
            }
            for (int s0 = 0; s0 <= 1; s0++)
                for (int s1 = 0; s1 <= 1; s1++)
                    for (int s2 = 0; s2 <= 1; s2++) {
                        int sel = (s2 << 2) | (s1 << 1) | s0;
                        for (int[] inputs : new int[][]{{0,0,0,0,0,0,0,0}, {0,1,1,0,1,0,1,1}}) {
                            int y = inputs[sel];
                            rows.add(new String[]{
                                ""+inputs[0],""+inputs[1],""+inputs[2],""+inputs[3],
                                ""+inputs[4],""+inputs[5],""+inputs[6],""+inputs[7],
                                ""+s0,""+s1,""+s2,""+y});
                        }
                    }
            return rows;
        }

        // ---------------- DEMUX FAMILY ----------------
        case "1X2 DEMUX" -> {
            headers.addAll(List.of("D", "S", "Y0", "Y1"));
            List<String[]> rows = new ArrayList<>();
            for (int D = 0; D <= 1; D++)
                for (int S = 0; S <= 1; S++) {
                    int Y0 = (D == 1 && S == 0) ? 1 : 0;
                    int Y1 = (D == 1 && S == 1) ? 1 : 0;
                    rows.add(new String[]{""+D,""+S,""+Y0,""+Y1});
                }
            return rows;
        }

        case "1X4 DEMUX" -> {
            headers.addAll(List.of("D","S0","S1","Y0","Y1","Y2","Y3"));
            List<String[]> rows = new ArrayList<>();
            for (int D = 0; D <= 1; D++)
                for (int s0 = 0; s0 <= 1; s0++)
                    for (int s1 = 0; s1 <= 1; s1++) {
                        int sel = (s1 << 1) | s0;
                        int[] y = new int[4];
                        if (D == 1) y[sel] = 1;
                        rows.add(new String[]{""+D,""+s0,""+s1,""+y[0],""+y[1],""+y[2],""+y[3]});
                    }
            return rows;
        }

        case "1X8 DEMUX" -> {
            headers.addAll(List.of("D","S0","S1","S2","Y0","Y1","Y2","Y3","Y4","Y5","Y6","Y7"));
            List<String[]> rows = new ArrayList<>();
            for (int D = 0; D <= 1; D++)
                for (int s0 = 0; s0 <= 1; s0++)
                    for (int s1 = 0; s1 <= 1; s1++)
                        for (int s2 = 0; s2 <= 1; s2++) {
                            int sel = (s2 << 2) | (s1 << 1) | s0;
                            int[] y = new int[8];
                            if (D == 1) y[sel] = 1;
                            rows.add(new String[]{
                                ""+D,""+s0,""+s1,""+s2,
                                ""+y[0],""+y[1],""+y[2],""+y[3],
                                ""+y[4],""+y[5],""+y[6],""+y[7]
                            });
                        }
            return rows;
        }

        // ---------------- ENCODER ----------------
        case "4X2 ENCODER" -> {
            headers.addAll(List.of("D0","D1","D2","D3","Y1","Y0"));
            List<String[]> rows = new ArrayList<>();
            for (int d0=0; d0<=1; d0++)
                for (int d1=0; d1<=1; d1++)
                    for (int d2=0; d2<=1; d2++)
                        for (int d3=0; d3<=1; d3++) {
                            int code=-1;
                            if (d0==1) code=0;
                            else if (d1==1) code=1;
                            else if (d2==1) code=2;
                            else if (d3==1) code=3;
                            int y1=(code>>1)&1, y0=code&1;
                            rows.add(new String[]{""+d0,""+d1,""+d2,""+d3,""+y1,""+y0});
                        }
            return rows;
        }

        // ---------------- DECODER ----------------
        case "2X4 DECODER" -> {
            headers.addAll(List.of("A","B","Y0","Y1","Y2","Y3"));
            List<String[]> rows = new ArrayList<>();
            for (int A=0; A<=1; A++)
                for (int B=0; B<=1; B++) {
                    int sel=(B<<1)|A;
                    int[] y=new int[4];
                    y[sel]=1;
                    rows.add(new String[]{""+A,""+B,""+y[0],""+y[1],""+y[2],""+y[3]});
                }
            return rows;
        }
    }
    return null; // no match → fall back to manual truth table
}

// Helper to populate the auto-generated table
private void populateTruthTable(List<String> headers, List<String[]> rows) {
    TableView<String[]> table = new TableView<>();
    table.setPrefWidth(700);
    table.setPrefHeight(500);

    for (int i = 0; i < headers.size(); i++) {
        final int colIndex = i;
        TableColumn<String[], String> col = new TableColumn<>(headers.get(i));
        col.setCellValueFactory(data ->
            new javafx.beans.property.SimpleStringProperty(data.getValue()[colIndex])
        );
        table.getColumns().add(col);
    }

    table.getItems().addAll(rows);

    Dialog<Void> dialog = new Dialog<>();
    dialog.setTitle("Auto Truth Table");
    dialog.setResizable(true);

    VBox box = new VBox(10, table);
    box.setPadding(new Insets(10));
    dialog.getDialogPane().setContent(box);
    dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
    dialog.showAndWait();
}
 

// complete evaluateAll, pinSceneCenter, save/load and main to finish the file
    // ---------- EVALUATION ----------
 // ---------- EVALUATION ----------
private void evaluateAll() {
    // If power is OFF — simulate full shutdown
    if (!powerToggle.isSelected()) {
        // 1️⃣ Force all IC outputs LOW
        for (ICBase ic : new ArrayList<>(ics)) {
            for (Map.Entry<Integer, Pin> en : ic.pins.entrySet()) {
                if (en.getValue().type == PinType.OUTPUT) en.getValue().value = false;
            }
        }

        // 2️⃣ Turn off all LEDs (outputs)
        for (ExternalLED led : externalLEDs) {
            led.pin.value = false;
            led.refresh();
        }

        // 3️⃣ Turn off all input indicators (switch LEDs)
        for (ExternalSwitch sw : externalSwitches) {
            sw.node.setFill(Color.DARKRED);
        }

        // 4️⃣ Reset disconnected LEDs and update wires
        resetDisconnectedLEDs();
        for (Wire w : wires) w.redraw();
        return;
    }

    // If power is ON — run normal logic simulation
    for (ICBase ic : ics) ic.updateLogic();

    // Refresh output LEDs
    for (ExternalLED led : externalLEDs) led.refresh();

    // Restore switch LED color (Green = ON)
    for (ExternalSwitch sw : externalSwitches) {
        sw.node.setFill(sw.tb.isSelected() ? Color.LIMEGREEN : Color.DARKRED);
    }

    resetDisconnectedLEDs();

    // Redraw wires to stay aligned with components
    for (Wire w : wires) w.redraw();
}
// ---------- SIMULATION MODE ----------
private Timeline simulationLoop;

private void toggleSimulation(ToggleButton btn) {
    if (btn.isSelected()) {
        btn.setText("Stop Simulation");
        btn.setStyle("-fx-background-color: linear-gradient(#27ae60,#1e8449); -fx-text-fill: white;");

        // Disable manual switches during simulation
        for (ExternalSwitch sw : externalSwitches) {
            sw.tb.setDisable(true);
        }

        // Start continuous evaluation (for clock-based changes)
        simulationLoop = new Timeline(new KeyFrame(Duration.millis(100), e -> evaluateAll()));
        simulationLoop.setCycleCount(Animation.INDEFINITE);
        simulationLoop.play();

        // Add tick marks to indicate powered ICs
        for (ICBase ic : ics) {
            Text tick = new Text("✔");
            tick.setFont(Font.font("Consolas", FontWeight.BOLD, 18));
            tick.setFill(Color.LIMEGREEN);
            tick.setLayoutX(130);
            tick.setLayoutY(20);
            ic.group.getChildren().add(tick);
        }

    } else {
        btn.setText("Simulate Circuit");
        btn.setStyle("-fx-background-color: linear-gradient(#e67e22,#d35400); -fx-text-fill: white;");

        // Enable switches again
        for (ExternalSwitch sw : externalSwitches) {
            sw.tb.setDisable(false);
        }

        // Stop continuous simulation
        if (simulationLoop != null) simulationLoop.stop();

        // Remove tick marks
        for (ICBase ic : ics) {
            ic.group.getChildren().removeIf(node -> node instanceof Text && ((Text) node).getText().equals("✔"));
        }
    }
}
// ---------- MISSING HELPERS ----------

private ClockPulse clockPulseInstance = null;

private void togglePower() {
    // Toggle visual style and simulation state
    stylePowerButton();
    evaluateAll();
}

// Trigger a single short clock pulse (creates a ClockPulse widget if missing)
private void triggerClockPulse() {
    if (clockPulseInstance == null) {
        clockPulseInstance = new ClockPulse(1100, 40);
        board.getChildren().add(clockPulseInstance.group);
        makeDraggable(clockPulseInstance.group);
    }
    // toggle on, short delay, toggle off
    clockPulseInstance.toggleClock();
    PauseTransition pt = new PauseTransition(Duration.millis(220));
    pt.setOnFinished(e -> {
        clockPulseInstance.toggleClock();
    });
    pt.play();
}

// Clears all wire connections
private void clearConnections() {
    for (Wire w : new ArrayList<>(wires)) {
        w.remove();
    }
    wires.clear();
    evaluateAll();
}

// ---------- UTILITIES ----------
    private Point2D pinSceneCenter(Pin p) {
        Bounds b;
        if (p.owner == null) {
            // external switches
            for (ExternalSwitch s : externalSwitches) {
                if (s.pin == p) {
                    b = s.node.localToScene(s.node.getBoundsInLocal());
                    return new Point2D((b.getMinX() + b.getMaxX()) / 2, (b.getMinY() + b.getMaxY()) / 2);
                }
            }
            // external leds
            for (ExternalLED l : externalLEDs) {
                if (l.pin == p) {
                    b = l.node.localToScene(l.node.getBoundsInLocal());
                    return new Point2D((b.getMinX() + b.getMaxX()) / 2, (b.getMinY() + b.getMaxY()) / 2);
                }
            }
            // power/gnd
            if (vccNode != null && vccNode.pin == p) {
                b = vccNode.circle.localToScene(vccNode.circle.getBoundsInLocal());
                return new Point2D((b.getMinX() + b.getMaxX()) / 2, (b.getMinY() + b.getMaxY()) / 2);
            }
            if (gndNode != null && gndNode.pin == p) {
                b = gndNode.circle.localToScene(gndNode.circle.getBoundsInLocal());
                return new Point2D((b.getMinX() + b.getMaxX()) / 2, (b.getMinY() + b.getMaxY()) / 2);
            }
            return new Point2D(0, 0);
        } else {
            Circle c = p.visual;
            b = c.localToScene(c.getBoundsInLocal());
            return new Point2D((b.getMinX() + b.getMaxX()) / 2, (b.getMinY() + b.getMaxY()) / 2);
        }
    }

    // ---------- SAVE / LOAD ----------
    private String ownerKey(Pin p) {
        if (p.owner == null) {
            for (int i = 0; i < externalSwitches.size(); i++) if (externalSwitches.get(i).pin == p) return "SW" + i;
            for (int i = 0; i < externalLEDs.size(); i++) if (externalLEDs.get(i).pin == p) return "LED" + i;
            if (vccNode != null && vccNode.pin == p) return "VCC";
            if (gndNode != null && gndNode.pin == p) return "GND";
            return "EXT";
        } else {
            for (int i = 0; i < ics.size(); i++) if (ics.get(i) == p.owner) return "IC" + i;
            return "IC?";
        }
    }

    private Pin findPinByKey(String key, int pinNumber, Map<String, ICBase> icMap, Map<String, ExternalSwitch> swMap, Map<String, ExternalLED> ledMap) {
        if ("VCC".equals(key)) return vccNode.pin;
        if ("GND".equals(key)) return gndNode.pin;
        if (key.startsWith("IC")) {
            ICBase ic = icMap.get(key);
            if (ic != null) return ic.pins.get(pinNumber);
        }
        if (key.startsWith("SW")) {
            ExternalSwitch s = swMap.get(key);
            if (s != null) return s.pin;
        }
        if (key.startsWith("LED")) {
            ExternalLED l = ledMap.get(key);
            if (l != null) return l.pin;
        }
        return null;
    }

    private void saveCircuit(Stage stage) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save Circuit");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Virtual IC file", "*.vic"));
        File f = fc.showSaveDialog(stage);
        if (f == null) return;
        try (PrintWriter pw = new PrintWriter(new FileWriter(f))) {
            pw.println("POWER=" + powerToggle.isSelected());
            // switches
            for (int i = 0; i < externalSwitches.size(); i++) {
                ExternalSwitch s = externalSwitches.get(i);
                pw.println(String.format("SW,%d,%.2f,%.2f,%b", i, s.group.getLayoutX(), s.group.getLayoutY(), s.tb.isSelected()));
            }
            // leds
            for (int i = 0; i < externalLEDs.size(); i++) {
                ExternalLED l = externalLEDs.get(i);
                pw.println(String.format("LED,%d,%.2f,%.2f", i, l.group.getLayoutX(), l.group.getLayoutY()));
            }
            // ics
            for (int i = 0; i < ics.size(); i++) {
                ICBase ic = ics.get(i);
                pw.println(String.format("IC,%d,%s,%.2f,%.2f", i, ic.title.getText(), ic.group.getLayoutX(), ic.group.getLayoutY()));
            }
            // wires
            for (Wire w : wires) {
                String a = ownerKey(w.a);
                String b = ownerKey(w.b);
                pw.println(String.format("WIRE,%s,%s,%d,%d", a, b, w.a.number, w.b.number));
            }
            pw.flush();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void loadCircuit(Stage stage) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Load Circuit");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Virtual IC file", "*.vic"));
        File f = fc.showOpenDialog(stage);
        if (f == null) return;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            // clear current
            for (Wire w : new ArrayList<>(wires)) w.remove();
            wires.clear();
            for (ICBase ic : new ArrayList<>(ics)) { board.getChildren().remove(ic.group); ics.remove(ic); }
            for (ExternalLED l : new ArrayList<>(externalLEDs)) { board.getChildren().remove(l.group); externalLEDs.remove(l); }
            for (ExternalSwitch s : new ArrayList<>(externalSwitches)) { board.getChildren().remove(s.group); externalSwitches.remove(s); }

            Map<String, ICBase> icMap = new HashMap<>();
            Map<String, ExternalSwitch> swMap = new HashMap<>();
            Map<String, ExternalLED> ledMap = new HashMap<>();

            List<String> wireLines = new ArrayList<>();

            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                if (line.startsWith("POWER=")) {
                    powerToggle.setSelected(Boolean.parseBoolean(line.substring(6)));
                    stylePowerButton();
                    continue;
                }
                String[] toks = line.split(",");
                switch (toks[0]) {
                    case "SW" -> {
                        int idx = Integer.parseInt(toks[1]);
                        double lx = Double.parseDouble(toks[2]);
                        double ly = Double.parseDouble(toks[3]);
                        boolean on = Boolean.parseBoolean(toks[4]);
                        ExternalSwitch s = new ExternalSwitch(lx, ly, idx + 1);
                        s.group.setLayoutX(lx); s.group.setLayoutY(ly);
                        s.tb.setSelected(on); s.tb.setText(on ? "1" : "0");
                        s.node.setFill(on ? Color.LIMEGREEN : Color.DARKRED);
                        s.pin.setValue(on);
                        externalSwitches.add(s);
                        board.getChildren().add(s.group);
                        swMap.put("SW" + idx, s);
                    }
                    case "LED" -> {
                        int idx = Integer.parseInt(toks[1]);
                        double lx = Double.parseDouble(toks[2]);
                        double ly = Double.parseDouble(toks[3]);
                        ExternalLED l = new ExternalLED(lx, ly, idx + 1);
                        l.group.setLayoutX(lx); l.group.setLayoutY(ly);
                        externalLEDs.add(l);
                        board.getChildren().add(l.group);
                        ledMap.put("LED" + idx, l);
                    }
                    case "IC" -> {
                        int idx = Integer.parseInt(toks[1]);
                        String name = toks[2];
                        double lx = Double.parseDouble(toks[3]);
                        double ly = Double.parseDouble(toks[4]);
                        ICBase ic;
                        switch (name) {
                            case "7400", "7400 NAND" -> ic = new IC7400(lx, ly);
                            case "7402", "7402 NOR" -> ic = new IC7402(lx, ly);
                            case "7408", "7408 AND" -> ic = new IC7408(lx, ly);
                            case "7432", "7432 OR" -> ic = new IC7432(lx, ly);
                            case "7404", "7404 NOT" -> ic = new IC7404(lx, ly);
                            case "7486", "7486 XOR" -> ic = new IC7486(lx, ly);
                            case "7487", "7487 XNOR" -> ic = new IC7487(lx, ly);
                            default -> ic = new IC7400(lx, ly);
                        }
                        ic.group.setLayoutX(lx); ic.group.setLayoutY(ly);
                        ics.add(ic);
                        board.getChildren().add(ic.group);
                        icMap.put("IC" + idx, ic);
                    }
                    case "WIRE" -> wireLines.add(line);
                }
            }

            // second pass: add wires
            for (String wln : wireLines) {
                String[] toks = wln.split(",");
                if (toks.length < 5) continue;
                String aStr = toks[1], bStr = toks[2];
                int pinA = Integer.parseInt(toks[3]);
                int pinB = Integer.parseInt(toks[4]);
                Pin pa = findPinByKey(aStr, pinA, icMap, swMap, ledMap);
                Pin pb = findPinByKey(bStr, pinB, icMap, swMap, ledMap);
                if (pa != null && pb != null) createWire(pa, pb);
            }

            evaluateAll();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
    // ---------- Drag Helpers ----------
private static class Delta {
    double x, y;
}

private void makeDraggable(Group g) {
    final Delta dragDelta = new Delta();
    g.setOnMousePressed(e -> {
        if (e.getButton() != MouseButton.PRIMARY) return;
        dragDelta.x = e.getSceneX() - g.getLayoutX();
        dragDelta.y = e.getSceneY() - g.getLayoutY();
    });
    g.setOnMouseDragged(e -> {
        if (e.getButton() != MouseButton.PRIMARY) return;
        g.setLayoutX(e.getSceneX() - dragDelta.x);
        g.setLayoutY(e.getSceneY() - dragDelta.y);
        for (Wire w : wires) w.redraw(); // refresh wires dynamically
    });
}

private void makeDraggable(Pane p) {
    final Delta dragDelta = new Delta();
    p.setOnMousePressed(e -> {
        if (e.getButton() != MouseButton.PRIMARY) return;
        dragDelta.x = e.getSceneX() - p.getLayoutX();
        dragDelta.y = e.getSceneY() - p.getLayoutY();
    });
    p.setOnMouseDragged(e -> {
        if (e.getButton() != MouseButton.PRIMARY) return;
        p.setLayoutX(e.getSceneX() - dragDelta.x);
        p.setLayoutY(e.getSceneY() - dragDelta.y);
        for (Wire w : wires) w.redraw(); // refresh wire geometry
    });
}


    // ---------- MAIN ----------
    public static void main(String[] args) {
        launch(args);
    }
}
