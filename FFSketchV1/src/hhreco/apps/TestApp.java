/*
 * $Id: TestApp.java,v 1.9 2003/10/15 00:07:31 hwawen Exp $
 *
 * Copyright (c) 2003 The Regents of the University of California.
 * All rights reserved. See the file COPYRIGHT for details.
 */
package hhreco.apps;

import diva.util.java2d.Polyline2D;
import diva.canvas.BasicZList;
import diva.canvas.CompositeFigure;
import diva.canvas.Figure;
import diva.canvas.toolbox.BasicFigure;
import diva.sketch.JSketch;
import diva.sketch.SketchPane;
import diva.sketch.SketchController;
import diva.sketch.StrokeSymbol;
import diva.sketch.SketchModel;
import hhreco.classification.*;
import hhreco.recognition.*;
import hhreco.toolbox.*;
import javax.swing.*;
import javax.swing.border.EtchedBorder;
import java.awt.*;
import java.awt.List;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Rectangle2D;
import java.awt.event.*;
import java.util.*;
import java.io.*;

/**
 * An application that sets up a recognition system and uses it to
 * recognize sketched symbols.
 *
 * @author Heloise Hse   (hwawen@eecs.berkeley.edu)
 */
public class TestApp extends JFrame {
    public final static String REJECT = "reject";
    private SketchController _controller;
    private JTextField _result;
    private HHRecognizer _recognizer;
    private Choice _choice;
    private MSTrainingModel _model = new MSTrainingModel();
    private Writer _out;
    private ApproximateStrokeFilter _approx = new ApproximateStrokeFilter(1.0);
    private InterpolateStrokeFilter _interp = new InterpolateStrokeFilter(10.0);
    
    public static void main(String argv[]) {
	TestApp app = null; 
        if(argv.length == 1){
            System.out.println("training file: " + argv[0]);
            app = new TestApp(argv[0]);
        }
        else{
            throw new UnsupportedOperationException("Must provide a training file");
        }
        app.setSize(900,500);
        app.setVisible(true);
    }

    /**
     * Create a test application that trains the recognition system
     * with the specified training file.
     */
    public TestApp(String trainingFile) {
        super(trainingFile);
        addWindowListener(new MyWindowListener());
        try{
            _out = new BufferedWriter(new FileWriter("corrections.sml"));
            MSTrainingWriter.writeHeader(_out);
            _out.write("<"+MSTrainingParser.MODEL_TAG+">\n");
            BufferedReader br = new BufferedReader(new FileReader(trainingFile));
            MSTrainingParser parser = new MSTrainingParser();
            MSTrainingModel trainModel = (MSTrainingModel)parser.parse(br);
            _model = new MSTrainingModel();
            for(Iterator iter = trainModel.types(); iter.hasNext();){
                String type = (String)iter.next();
                for(Iterator iter2 = trainModel.positiveExamples(type); iter2.hasNext();){
                    TimedStroke[] strokes = (TimedStroke[])iter2.next();
                    strokes = HHRecognizer.preprocess(strokes,_approx,_interp,null);
                    _model.addPositiveExample(type, strokes);
                }
            }
            _recognizer = new HHRecognizer();
            _recognizer.train(_model);
            initUI(_model);
        }
        catch(Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

  
    /**
     * Initialize the user interface of the application.  Display the
     * symbol icons in the top row, provide a drawing surface, command
     * buttons, etc.
     */
    private void initUI(MSTrainingModel model){
        setBackground(Color.white);
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        getContentPane().add(panel);
        JPanel iconPanel = createIconPanel(model);
        JSketch jsketch = new JSketch();
        panel.add("Center", jsketch);
        SketchPane pane = jsketch.getSketchPane();
        _controller = pane.getSketchController();
        
        JButton butReco = new JButton("Recognize");
        butReco.addActionListener(new ButtonRecognizeListener());
        _result = new JTextField(10);
        _result.setEditable(false);
        _result.setBackground(Color.white);
        JPanel recoPanel = new JPanel(new BorderLayout());
        recoPanel.setBorder(BorderFactory.createTitledBorder("Recognition Panel"));
        recoPanel.add("East",butReco);
        recoPanel.add("Center",_result);

        _choice = new Choice();
        String[] types = new String[model.getTypeCount()];
        int i=0;
        for(Iterator iter = model.types(); iter.hasNext();){
            types[i++]=(String)iter.next();
        }
        Arrays.sort(types);
        for(i=0; i<types.length; i++){
            _choice.add(types[i]);
        }
        JButton submit = new JButton("Submit");
        submit.addActionListener(new CorrectionListener());
        JPanel corrPanel = new JPanel(new BorderLayout());
        corrPanel.setBorder(BorderFactory.createTitledBorder("Correction Panel"));
        corrPanel.add("Center",_choice);
        corrPanel.add("East",submit);
        
        JButton butClear = new JButton("Clear Screen");
        butClear.addActionListener(new ButtonClearListener());
        JButton butExit = new JButton("Exit");
        butExit.addActionListener(new ExitListener());
        JPanel buttonPanel = new JPanel(new GridLayout(1,2,10,10));
        buttonPanel.add(butClear);
        buttonPanel.add(butExit);
        buttonPanel.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.RAISED));
                
        JPanel controlPanel = new JPanel(new BorderLayout());
        JPanel p1 = new JPanel(new GridLayout(1,2,5,5));
        p1.add(recoPanel);
        p1.add(corrPanel);
        controlPanel.add("Center",p1);
        controlPanel.add("East",buttonPanel);
        
        panel.add("North",iconPanel);
        panel.add("South",controlPanel);
    }

    /**
     * Create a panel of valid symbol icons by displaying the first
     * example of each class.
     */
    private JPanel createIconPanel(MSTrainingModel model){
        int n = model.getTypeCount();
        JPanel p = new JPanel(new FlowLayout());
        for(Iterator iter = model.types(); iter.hasNext();){
            String type = (String)iter.next();
            for(Iterator iter2 = model.positiveExamples(type); iter2.hasNext();){
                TimedStroke[] strokes = (TimedStroke[])iter2.next();
                JButton button = new JButton(new SymbolIcon(type,strokes));
                button.setMargin(new Insets(10,5,10,5));//top,left,bottom,right
                button.setEnabled(true);
                p.add(button);
                break;
            }
        }
        return p;
    }

    /**
     * In addition to writing out the strokes of the corrected shape
     * to a file called "corrections.sml", the recognizer is also
     * retrained with this shape added to the correct training class
     * on the fly.
     *
     * The corrected shapes are not automatically appended to the
     * initial training file so as to keep the file intact.  They can
     * be appended either manually or by using
     * hhreco/apps/MergeFiles.java program.  If desired,
     * developers can easily program it to do so by adding the
     * corrections to the initial training model and writing out the
     * training model at the end of the session.
     */
    private class CorrectionListener implements ActionListener {
        public void actionPerformed(ActionEvent e){
            String type = _choice.getSelectedItem();
            //write the corrected example to file
            TimedStroke[] strokes = collectStrokes();
            if(strokes==null){
                return;
            }
            try{
                _out.write("<" + MSTrainingParser.TYPE_TAG + " " + MSTrainingParser.NAME_TAG + "=\"");
                _out.write(type);
                _out.write("\">\n");
                MSTrainingWriter.writeExample(strokes, MSTrainingModel.POSITIVE, _out);
                _out.write("</" + MSTrainingParser.TYPE_TAG + ">\n");
                _out.flush();
                strokes = HHRecognizer.preprocess(strokes,_approx,_interp,null);
                //MSTrainingModel m = new MSTrainingModel();
                //m.addPositiveExample(type,strokes);
                //_recognizer.addAndRetrain(m);
                _recognizer.addAndRetrain(type,strokes);
                System.out.println("Recognizer retrained");
            }
            catch(Exception ex){
                ex.printStackTrace();
            }
        }
    }

    /**
     * The icon display for each symbol in the training set.
     */
    private static class SymbolIcon implements Icon {
        public static int WIDTH = 50;
        public static int HEIGHT = 50;
        private TimedStroke[] _strokes;
        private Rectangle2D _bbox;
        private String _type;
        public SymbolIcon(String type, TimedStroke[] strokes){
            _type = type;

            _strokes = new TimedStroke[strokes.length];
            for(int i=0; i<strokes.length; i++){
                _strokes[i] = new TimedStroke(strokes[i]);
            }
            Util.normScaling(_strokes,HEIGHT,WIDTH);
            _bbox = new Rectangle2D.Double();
            for(int i=0; i<_strokes.length; i++){
                _bbox.add(_strokes[i].getBounds2D());
            }
        }

        public String getType(){
            return _type;
        }

        public int getIconHeight(){
            return WIDTH;
        }

        public int getIconWidth(){
            return HEIGHT;
        }

        public void paintIcon(Component c, Graphics g, int x, int y){
            Dimension dim = c.getSize();
            g.setColor(Color.black);
            g.drawString(_type,x,y);
            double dx = (double)dim.getWidth()/2-(double)_bbox.getWidth()/2;
            double dy = (double)dim.getHeight()/2-(double)_bbox.getHeight()/2;
            for(int i=0; i<_strokes.length; i++){
                paintStroke(g, _strokes[i], dx, dy);
            }
        }


        public void paintStroke(Graphics g, TimedStroke s, double dx, double dy) {
            for(int j=0; j<s.getVertexCount()-1; j++){
                double x1 = s.getX(j)+dx;
                double y1 = s.getY(j)+dy;
                double x2 = s.getX(j+1)+dx;
                double y2 = s.getY(j+1)+dy;
                g.drawLine((int)x1,(int)y1,(int)x2,(int)y2);
            }
        }
    }

    /**
     * Clear the drawing screen.
     */
    private void clear(){
        _controller.setSketchModel(new SketchModel());
        _result.setText("");
    }

    /**
     * Clear the screen when the button has been pressed.
     */
    private class ButtonClearListener implements ActionListener {
        public void actionPerformed(ActionEvent evt) {
            clear();
        }
    }

    /**
     * Invoke the recognizer when the button has been pressed.
     */
    private class ButtonRecognizeListener implements ActionListener {
        public void actionPerformed(ActionEvent evt) {
            if(_controller.getSketchModel().getSymbolCount()==0){
                System.out.println("No stroke");
            }
            else {
                TimedStroke strokes[] = collectStrokes();
                if(strokes==null){
                    return;
                }
                if(_recognizer!=null){
                    HHRecognizer.preprocess(strokes,_approx,_interp,null);
                    RecognitionSet rset = _recognizer.sessionCompleted(strokes);
                    updateListUI(rset);
                }
                else{
                    System.out.println("Recognizer has not been trained");
                }
            }
        }
    }

    /**
     * Output the recognition result.
     */
    private void updateListUI(RecognitionSet rset){
        if(rset.getRecognitionCount()==0){
            _result.setText(REJECT);
        }
        else{
            Recognition r = rset.getHighestValueRecognition();
            _result.setText(r.getType().getID());
        }
    }

    /**
     * Collect the strokes on the screen in the time sequence
     * sketched.
     */
    private TimedStroke[] collectStrokes(){
        SketchModel m = _controller.getSketchModel();
        int num = m.getSymbolCount();
        if(num==0){
            return null;
        }
        TimedStroke[] strokes = new TimedStroke[num];
        //add strokes into session in the order they're sketched note
        //that the stroke being returned by the sketch model is last
        //in, first out.  Also a conversion is done from
        //diva.sketch.recognition.TimedStroke object to
        //hhreco.recognition.TimedStroke.
        int j=num-1;
        for(Iterator i = m.symbols(); i.hasNext(); ) {
            StrokeSymbol symbol = (StrokeSymbol)i.next();
            diva.sketch.recognition.TimedStroke s = symbol.getStroke();
            TimedStroke s2 = new TimedStroke(s.getVertexCount());
            for(int k=0; k<s.getVertexCount(); k++){
                s2.addVertex(s.getX(k),s.getY(k),s.getTimestamp(k));
            }
            strokes[j--]=s2;
        }
        return strokes;
    }

    /**
     * Close the output file that records the corrected symbols when a
     * window closing event is received.
     */
    private class MyWindowListener extends WindowAdapter {
        public void windowClosing(WindowEvent e) {
            exit();
        }
    }

    private class ExitListener implements ActionListener {
        public void actionPerformed(ActionEvent evt) {
            exit();
        }
    }

    private void exit(){
        try{
            _out.write("</" + MSTrainingParser.MODEL_TAG + ">\n");
            _out.close();
        }
        catch(Exception ex){
            ex.printStackTrace();
        }
        System.exit(0);
    }
}
