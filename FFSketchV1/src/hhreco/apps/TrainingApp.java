/*
 * $Id: TrainingApp.java,v 1.3 2003/10/17 16:59:54 hwawen Exp $
 *
 * Copyright (c) 2003 The Regents of the University of California.
 * All rights reserved. See the file COPYRIGHT for details.
 */
package hhreco.apps;

import hhreco.recognition.MSTrainingWriter;
import hhreco.recognition.MSTrainingModel;
import hhreco.recognition.TimedStroke;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.io.*;

/**
 * A symbol training application allowing a user to create his
 * or her own symbol training file.
 *
 * @author Heloise Hse   (hwawen@eecs.berkeley.edu)
 */
public class TrainingApp extends JFrame {
    JTextField _input;
    MSTrainingWriter _twriter;
    diva.sketch.SketchController _controller;
    HashMap _shapes;//each type name maps to an ArrayList of TimedStrokes[]
    String _curShape = null;
    TextArea _msg;
    String _username;
    String _filename;
    boolean _hasBeenSaved=true;

    public static void main(String argv[]) {
        if(argv.length != 1){
            System.out.println("Please enter a user name.");
            System.exit(1);
        }
        String username = argv[0];
        String title = username+"'s multistroke gestures";
	TrainingApp app = new TrainingApp(title, username);
        app.setSize(500,500);
        app.setVisible(true);
    }

    /**
     * Create a training application window with the specified frame
     * title, and the training examples are saved in a file named
     * username.sml where username is as specified.
     */
    public TrainingApp (String title, String username){
        super(title);
        try {
            _username = username;
            JPanel panel = new JPanel();
            panel.setLayout(new BorderLayout());
            getContentPane().add(panel);
            
            diva.sketch.JSketch jsketch = new diva.sketch.JSketch();
            panel.add("Center", jsketch);
            diva.sketch.SketchPane pane = jsketch.getSketchPane();
            _controller = pane.getSketchController();
            
            _input = new JTextField(20);

            JButton but1 = new JButton("Add");
            but1.addActionListener(new ButtonAddListener());
            JButton but2 = new JButton("Clear");
            but2.addActionListener(new ButtonClearListener());
            JButton but3 = new JButton("Undo Add");
            but3.addActionListener(new ButtonUndoAddListener());
            JButton but4 = new JButton("Save");
            but4.addActionListener(new ButtonSaveListener());
            JButton but5 = new JButton("Done");
            but5.addActionListener(new ButtonDoneListener());
            JPanel buttons = new JPanel(new GridLayout(5,1,5,5));

            buttons.add(but1);//add
            buttons.add(but2);//clear
            buttons.add(but3);//undo add
            buttons.add(but4);//save
            buttons.add(but5);//done
            panel.add("East", buttons);

            JPanel label = new JPanel(new FlowLayout());
            label.add(new JLabel("Symbol name:"));
            label.add(_input);
            panel.add("North", label);
            
            String message = "Start " + _username+"'s training file\n";
            _msg = new TextArea(message,5,10);
            panel.add("South", _msg);
            
            _twriter = new MSTrainingWriter();
            System.out.println(_twriter);
            String curDir = System.getProperty("user.dir");
            _filename = curDir+File.separator+_username+".sml";
            _shapes = new HashMap();
        }
        catch(Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }
    
    private class ButtonDoneListener implements ActionListener {
        public void actionPerformed(ActionEvent evt) {
            if(!_hasBeenSaved){
                save();
            }
            System.exit(1);
        }
    }
    
    private class ButtonSaveListener implements ActionListener {
        public void actionPerformed(ActionEvent evt) {
            save();
        }
    }

    private void save(){
        try{
            BufferedWriter bw = new BufferedWriter(new FileWriter(_filename));
            MSTrainingModel tmodel = new MSTrainingModel();
            Set keys = _shapes.keySet();
            for(Iterator iter = keys.iterator(); iter.hasNext();){
                String key = (String)iter.next();
                ArrayList list = (ArrayList)_shapes.get(key);
                for(Iterator items = list.iterator(); items.hasNext();){
                    TimedStroke strokes[] = (TimedStroke [])items.next();
                    tmodel.addPositiveExample(key, strokes);
                }
            }
            _twriter.writeModel(tmodel,bw);
            _msg.append("Saved in file: " + _filename +"\n");
            clear();
            bw.close();
            _hasBeenSaved=true;
        }
        catch(Exception ex){
            ex.printStackTrace();
        }
    }
    
    private class ButtonUndoAddListener implements ActionListener {
        public void actionPerformed(ActionEvent evt) {
            if(_curShape == null){
                _msg.append("Nothing to undo\n");
            }
            else {
                ArrayList list = (ArrayList)_shapes.get(_curShape);
                if(list == null){
                    _msg.append("?No " + _curShape + " shapes?\n");
                }
                else if(list.size()==0){
                    _msg.append("0 "+ _curShape + ", nothing to remove\n");
                }
                else{ 
                    list.remove(list.size()-1);
                    _msg.append("Removed last " + _curShape +", " + list.size() + " left\n");
                }
            }
        }
    }
    
    private class ButtonAddListener implements ActionListener {
        public void actionPerformed(ActionEvent evt) {
            String type = _input.getText();
            if(type==null || type.length()==0){
                _msg.append("Please enter a symbol name.\n");
                
            }
            else {
                diva.sketch.SketchModel m = _controller.getSketchModel();
                int num = m.getSymbolCount();
                if(num==0){
                    _msg.append("Nothing to add\n");
                }
                else {
                    TimedStroke strokes[] = new TimedStroke[num];
                    //add strokes into session in the order they're
                    //sketched note that the stroke being returned by
                    //the sketch model is last in, first out.  Also a
                    //conversion is done from
                    //diva.sketch.recognition.TimedStroke object to
                    //hhreco.recognition.TimedStroke.
                    int j=num-1;
                    for(Iterator i = m.symbols(); i.hasNext(); ) {
                        diva.sketch.StrokeSymbol symbol =
                            (diva.sketch.StrokeSymbol)i.next();
                        diva.sketch.recognition.TimedStroke s = symbol.getStroke();
                        TimedStroke s2 = new TimedStroke(s.getVertexCount());
                        for(int k=0; k<s.getVertexCount(); k++){
                            s2.addVertex(s.getX(k),s.getY(k),s.getTimestamp(k));
                        }
                        strokes[j--]=s2;
                    }
                    ArrayList list = (ArrayList)_shapes.get(type);
                    if(list == null){
                        list = new ArrayList();
                        _shapes.put(type,list);
                    }
                    list.add(strokes);
                    _curShape = type;
                    clear();
                    _msg.append("Added " + type + " # " +list.size()+"\n");
                    _hasBeenSaved=false;
                }
            }
        }
    }

    private void clear(){
        _controller.setSketchModel(new diva.sketch.SketchModel());
    }
    
    private class ButtonClearListener implements ActionListener {
        public void actionPerformed(ActionEvent evt) {
            clear();
        }
    }
}
