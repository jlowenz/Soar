package edu.umich.visualsoar.ruleeditor;

import edu.umich.visualsoar.operatorwindow.*;
import edu.umich.visualsoar.MainFrame;
import edu.umich.visualsoar.util.*;
import edu.umich.visualsoar.misc.*;
import edu.umich.visualsoar.dialogs.*;
import edu.umich.visualsoar.datamap.SoarWorkingMemoryModel;
import edu.umich.visualsoar.graph.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.text.*;
import javax.swing.tree.*;
import java.io.*;
import javax.swing.event.*;
import java.beans.*;
import javax.swing.undo.*;
import java.util.*;
import edu.umich.visualsoar.parser.SoarParser;
import edu.umich.visualsoar.parser.SoarProduction;
import edu.umich.visualsoar.parser.ParseException;
import edu.umich.visualsoar.parser.SoarParserConstants;

// 3P
import threepenny.*;

/**
 * This is the rule editor window 
 * @author Brad Jones
 */
public class RuleEditor extends JInternalFrame {
 	// Data Members
	private OperatorNode associatedNode;
	private EditorPane editorPane = new EditorPane();
	private UndoManager undoManager = new IgnoreSyntaxColorUndoManager();
	private String fileName;
	private boolean change = false;
	private JLabel lineNumberLabel = new JLabel("Line:");
	private JLabel modifiedLabel = new JLabel("");
	
	private	String findString = null;
	private String replaceString = null;
	private boolean	findForward = true;
	private boolean	matchCase = false;
	private boolean wrapSearch = false;
	
		
	// Actions 
	private Action saveAction = new SaveAction();
	private Action revertToSavedAction = new RevertToSavedAction();
	private Action closeAction = new CloseAction();
	
	private Action undoAction = new UndoAction();
	private Action redoAction = new RedoAction();
	private Action cutAction = new DefaultEditorKit.CutAction();
	private Action copyAction = new DefaultEditorKit.CopyAction();
	private Action pasteAction = new DefaultEditorKit.PasteAction();
	private Action insertTextFromFileAction = new InsertTextFromFileAction();
	
	private Action commentOutAction = new CommentOutAction();
	private Action uncommentOutAction = new UncommentOutAction();

  private Action reDrawAction = new ReDrawAction();
  private Action reJustifyAction = new ReJustifyAction();

	private Action findAction = new FindAction();
	private FindAgainAction findAgainAction = new FindAgainAction();
	private ReplaceAction replaceAction = new ReplaceAction();
	private ReplaceAndFindAgainAction replaceAndFindAgainAction = new ReplaceAndFindAgainAction();
	private ReplaceAllAction replaceAllAction = new ReplaceAllAction();
	private Action findAndReplaceAction = new FindAndReplaceAction();
	
	private Action checkProductionsAction = new CheckProductionsAction();
	private Action tabCompleteAction = new TabCompleteAction();
  private Action autoSoarCompleteAction = new AutoSoarCompleteAction();

	// 3P
	// Menu item handlers for the STI operations in this window.
	private Action sendProductionToSoarAction = new SendProductionToSoarAction();
	private Action sendFileToSoarAction = new SendFileToSoarAction();
	private Action sendMatchesToSoarAction = new SendMatchesToSoarAction();
	private Action sendExciseProductionToSoarAction = new SendExciseProductionToSoarAction();
	
	private BackupThread backupThread;
	// Constructors
	/**
	 * Constructs a new JInternalFrame, sets its size and
	 * adds the editor pane to it
	 * @param in_fileName the file to which this RuleEditor is associated
	 */
	public RuleEditor(File inFileName,OperatorNode inNode) throws IOException {
		super(inNode.getUniqueName(),true,true,true,true);

		// Initalize my member variables
		associatedNode = inNode;
		fileName = inFileName.getPath();
		getData(inFileName);
		editorPane.setFont(new Font("Monospaced",Font.PLAIN,12));
		setBounds(0,0,250,100);
		initMenuBar();
		initLayout();
		//editorPane.setLineWrap(false);
		addVetoableChangeListener(new CloseListener());

    // Retile the internal frames after closing a window
    setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    addInternalFrameListener(new InternalFrameAdapter() {
      public void internalFrameClosing(InternalFrameEvent e) {

        if (change) {
          int answer = JOptionPane.showConfirmDialog(null,"Save Changes to " + fileName + "?",
									   "Unsaved Changes",JOptionPane.YES_NO_CANCEL_OPTION);
					if (answer == JOptionPane.CANCEL_OPTION) {
						return;
					} else if (answer == JOptionPane.YES_OPTION) {
						try {
							write();
						}
						catch(IOException ioe) {
							ioe.printStackTrace();
						}
					}
				}
        dispose();

        if(Preferences.getInstance().isAutoTilingEnabled())
          MainFrame.getMainFrame().getDesktopPane().performTileAction();
      }
     });

		registerDocumentListeners();
		backupThread = new BackupThread();
		backupThread.start();
		
		/*
		editorPane.addFocusListener(new FocusListener() {
			 public void focusGained(FocusEvent e) {
				 try {
				 		if(!isSelected()) {
				 			setSelected(true);
				 			//editorPane.requestFocus();
				 		}
				 } 
				 catch(java.beans.PropertyVetoException pve) { pve.printStackTrace(); }
			 }
			 public void focusLost(FocusEvent e) {
			 	try {
			 		if(isSelected()) {
			 			setSelected(false);
			 			hideCaret();
			 		}
			 	}
			 	catch(java.beans.PropertyVetoException pve) { pve.printStackTrace(); }
			 }
		});
		*/


    if(edu.umich.visualsoar.misc.Preferences.getInstance().isAutoSoarCompleteEnabled()) {
      KeyStroke dot = KeyStroke.getKeyStroke('.');
      Keymap keymap = editorPane.getKeymap();
      keymap.addActionForKeyStroke(dot, autoSoarCompleteAction);
      editorPane.setKeymap(keymap);
    }


		editorPane.addCaretListener(new CaretListener() {
			public void caretUpdate(CaretEvent e) {
				int offset =  e.getDot();

       try {
					lineNumberLabel.setText("Line: " + (1+editorPane.getLineOfOffset(offset)));
					//editorPane.requestFocus();
				} catch(BadLocationException ble) {
					ble.printStackTrace();
				}
			}
		});
		
		adjustKeymap();
	}

	/**
	 * Constructs a new JInternalFrame, sets its size and
	 * adds the editor pane to it.
   * This constructor is for opening external files not associated with the
   * project.
	 * @param in_fileName the file to which this RuleEditor is associated
	 */
	public RuleEditor(File inFileName) throws IOException {
		super(inFileName.getName(),true,true,true,true);

		// Initalize my member variables
		associatedNode = null;
		fileName = inFileName.getPath();
		getData(inFileName);

		editorPane.setFont(new Font("Monospaced",Font.PLAIN,14));

		setBounds(0,0,250,100);
		initMenuBarExternFile();
		initLayout();

		addVetoableChangeListener(new CloseListener());

		registerDocumentListeners();
		backupThread = new BackupThread();
		backupThread.start();

		editorPane.addCaretListener(new CaretListener() {
			public void caretUpdate(CaretEvent e) {
				int offset =  e.getDot();
				try {
					lineNumberLabel.setText("Line: " + (1+editorPane.getLineOfOffset(offset)));
					//editorPane.requestFocus();
				} catch(BadLocationException ble) {
					ble.printStackTrace();
				}
			}
		});
		adjustKeymap();
	}     // end of constructor


	private void registerDocumentListeners() {
		Document doc = editorPane.getDocument();
		
		doc.addDocumentListener(new DocumentListener() {
			public void insertUpdate(DocumentEvent e) {
				if (!change) {
					change = true;
					modifiedLabel.setText("Modified");
				}
			}
			public void removeUpdate(DocumentEvent e) {
				if (!change) {
					change = true;
					modifiedLabel.setText("Modified");
				}
			}
			public void changedUpdate(DocumentEvent e) {}
		});
		
		doc.addUndoableEditListener(undoManager);
	}
	
	private void adjustKeymap() {
		Keymap keymap = editorPane.getKeymap();
		keymap.removeKeyStrokeBinding(KeyStroke.getKeyStroke(KeyEvent.VK_F,Event.ALT_MASK)); 
		editorPane.setKeymap(keymap);
	
	}		
	
	/**
	 * A helper function to read in the data from a file into the editorPane so we don't have
	 * catch it in the constructor
	 * @param fn the file name
	 */
	private void getData(File fn) throws IOException {
		Reader fr = new edu.umich.visualsoar.util.TabRemovingReader(new FileReader(fn));
		editorPane.read(fr);
		fr.close();
	}
		
	/**
	 * Sets the current line number in the editorPane, puts the caret at the beginning of the
	 * line, and requests focus
	 * @param lineNum the desired lineNum to go to
	 */
	public void setLine(int lineNum) {
		try {
			editorPane.setCaretPosition(editorPane.getDocument().getLength() - 1);
			int offset = editorPane.getLineStartOffset(lineNum - 1);
			editorPane.setCaretPosition(offset);
		} 
		catch(BadLocationException ble) {
			ble.printStackTrace();
		}
		editorPane.requestFocus();
	}
	
	public int getNumberOfLines() {
		return editorPane.getLineCount();
	}
	
	/**
	 * Returns the node this rule editor is associated with
	 * @return the associated node
	 */
	public OperatorNode getNode() {
		return associatedNode;
	}
	 
	
	
	/**
	 * Finds the a string in the document past the caret, if it finds it, it selects it
	 * @return whether or not it found the string
	 */
	private boolean findForward() {
		String searchString;
		
		if (matchCase) 
			searchString = findString;
		else
			searchString = findString.toLowerCase();
		Document doc = editorPane.getDocument();
		int caretPos = editorPane.getCaretPosition();

		int textlen = doc.getLength() - caretPos;
		try {
			String text = doc.getText(caretPos,textlen);
			if (! matchCase) {
				text = text.toLowerCase();
			}
			int start = text.indexOf(searchString);
			
			if ((start == -1) && (wrapSearch == true)) { // search the wrapped part
				text = doc.getText(0,caretPos);
				caretPos = 0;
				start = text.indexOf(searchString);
			}

			if (start != -1) {
				int end = start + findString.length();
				editorPane.setSelectionStart(caretPos + start);
				editorPane.setSelectionEnd(caretPos + end);
				return true;
			}
			else { // string not found
				return false;
			}
		} catch(BadLocationException ble) { ble.printStackTrace(); }
		return false;
	}
	
	/**
	 * Finds the a string in the document before the caret or before the selection
	 * point, if it finds it, it selects it
	 * @return whether or not it found the string
	 */
	private boolean findBackward() {
		String searchString;
		if (matchCase)
			searchString = findString;
		else
			searchString = findString.toLowerCase();
		Document doc = editorPane.getDocument();
		int caretPos = editorPane.getSelectionStart();
		try {
			String text = doc.getText(0,caretPos);
			if (! matchCase) {
				text = text.toLowerCase();
			}
			int start = text.lastIndexOf(searchString);

			if ((start == -1) && (wrapSearch == true)) { // seach the wrapped part 
				int textlen = doc.getLength() - caretPos;
				text = doc.getText(caretPos, textlen);
				start = caretPos + text.lastIndexOf(searchString);
			}
			
			if (start != -1) {
				int end = start + findString.length();
				editorPane.setSelectionStart(start);
				editorPane.setSelectionEnd(end);
				return true;
			}
			else { // string not found
				return false;
			}
		} catch(BadLocationException ble) { ble.printStackTrace(); }	    
		return false;
	}
	
	public String getAllText() {
		Document doc = editorPane.getDocument();
		String s = new String();
		try {
			s = doc.getText(0,doc.getLength());
		}
		catch(BadLocationException ble) { ble.printStackTrace(); }
		return s;
	}
	
	public void setFindReplaceData(String find, Boolean forward, Boolean caseSensitive, Boolean wrap) {
		setFindReplaceData(find, null, forward, caseSensitive, wrap);
	}
	
	public void setFindReplaceData(String find, String replace, Boolean forward, Boolean caseSensitive, Boolean wrap) {
		findString = find;
		replaceString = replace;
		findForward = forward.booleanValue();
		matchCase = caseSensitive.booleanValue();
		wrapSearch = wrap.booleanValue();

		if (findString != null) {
			findAgainAction.setEnabled(true);

			if (replaceString != null) {
				replaceAction.setEnabled(true);
				replaceAndFindAgainAction.setEnabled(true);
				replaceAllAction.setEnabled(true);
			}
		}
	}
	
	// 3P
	// This method returns the production that the cursor is currently over.
	// null is returned if a production cannot be found underneath the current
	// cursor position.
	public String GetProductionStringUnderCaret() {
		// Get the current position of the cursor in the editor pane
		int caretPos = editorPane.getCaretPosition();
		
		// Get the text in the editor
		String text = editorPane.getText();

		// Search backwards for the "sp " string which marks
		// the start of a production.
		int nProductionStartPos = text.lastIndexOf("sp ",caretPos);
		if (nProductionStartPos == -1)
		{
			return null;
		}
		
		// Now search for the first opening brace for this production
		int nFirstOpenBrace=text.indexOf('{', nProductionStartPos);
		if (nFirstOpenBrace == -1)
		{
			return null;
		}
		
		// Go through the string looking for the closing brace
		int nNumOpenBraces=1;
		int nCurrentSearchPos=nFirstOpenBrace+1;
		while (nCurrentSearchPos < text.length() && nNumOpenBraces > 0)
		{
			// Keep track of our open brace count
			if (text.charAt(nCurrentSearchPos) == '{')
			{
				nNumOpenBraces++;
			}
			if (text.charAt(nCurrentSearchPos) == '}')
			{
				nNumOpenBraces--;
			}
			
			// Advance to the next character
			nCurrentSearchPos++;
		}
		
		// We should have zero open braces
		if (nNumOpenBraces != 0)
		{
			return null;
		}
		
		// The last brace marks the end of the production
		int nProductionEndPos=nCurrentSearchPos;
		
		// Our cursor position should not be past the last brace.  If this is
		// the case, it means that our cursor is not really inside of a production.
		if (nProductionEndPos < caretPos)
		{
			return null;
		}
		
		// Get the production substring
		String sProductionString=text.substring(nProductionStartPos, nProductionEndPos);
		
		// Return the string to the caller
		return sProductionString;
	}

	// 3P
	// This method returns the production name that the cursor is currently over.
	// null is returned if a production cannot be found underneath the current
	// cursor position.
	public String GetProductionNameUnderCaret() {
		// Get the current position of the cursor in the editor pane
		int caretPos = editorPane.getCaretPosition();
		
		// Get the text in the editor
		String text = editorPane.getText();

		// Search backwards for the "sp " string which marks
		// the start of a production.
		int nProductionStartPos = text.lastIndexOf("sp ",caretPos);
		if (nProductionStartPos == -1)
		{
			return null;
		}
		
		// Now search for the first opening brace for this production
		int nFirstOpenBrace=text.indexOf('{', nProductionStartPos);
		if (nFirstOpenBrace == -1)
		{
			return null;
		}
		
		// Get the start of the name position
		int nStartPos=nFirstOpenBrace+1;
		if (nStartPos >= text.length())
		{
			return null;
		}
		
		// Now go through the editor text trying to find the end
		// of the name.  Right now we currently define the end
		// to be a space, newline, or '('.
		//
		// TODO: Is this the correct way to find the name?
		int nCurrentSearchIndex=nFirstOpenBrace+1;
		while (nCurrentSearchIndex < text.length())
		{
			// See if we have found a character which ends the name
			if (text.charAt(nCurrentSearchIndex) == ' ' ||
				text.charAt(nCurrentSearchIndex) == '\n' ||
				text.charAt(nCurrentSearchIndex) == '(')
			{
				break;
			}
			
			// Go to the next character
			nCurrentSearchIndex++;
		}
		
		// Last character in the name
		int nEndPos=nCurrentSearchIndex;
		
		// Return the name to the caller
		return text.substring(nStartPos, nEndPos);
	}
	
	/**
	 * Looks for the passed string in the document, if it is searching
	 * forward, then it searches for and instance of the string after the caret and selects it,
	 * if it is searching backwards then it selects the text either before the caret or
	 * before the start of the selection
	 */
	public void find() {
		boolean result;

		if (findForward)
			result = findForward(); 
		else 
			result = findBackward();
		if (!result)
			getToolkit().beep();
	}

  /**
   * Similiar to void find(), but returns result of search as boolean
   */
  public boolean findResult() {

    if (findForward)
      return (findForward() );
    else
      return (findBackward() );
  }

	
	/**
	 * Takes the selected text within the editorPane checks to see if that is the string
	 * that we are looking for, if it is then it replaces it and selects the new text
	 * @param toFind the string to find in the document to replace
	 * @param Replace the string to replace the toFind string
	 * @param forward whether or not to search forward
	 * @param caseSensitive whether or not this search is caseSensitive 
	 */
	public void replace() {
		String selString = editorPane.getSelectedText();
		if (selString == null) {
			getToolkit().beep();
			return;
		}
		boolean toReplace;
		if (matchCase) {
			toReplace = selString.equals(findString);
		}
		else {
			toReplace = selString.equalsIgnoreCase(findString);
		}
		if (toReplace) {
			editorPane.replaceSelection(replaceString);
			int end = editorPane.getCaretPosition(); 	
			int start = end - replaceString.length();
			editorPane.setSelectionStart(start);
			editorPane.setSelectionEnd(end);
		}
		else {
			getToolkit().beep();	
		}	
	}
	
	/**
	 * Replaces all instances (before/after) the caret with the specified string
	 * @param toFind the string to find in the document to replace
	 * @param Replace the string to replace the toFind string
	 * @param forward whether or not to search forward
	 * @param caseSensitive whether or not this search is caseSensitive
	 */
	public void replaceAll() {
		int count = 0;
		
		if(wrapSearch) {
			editorPane.setCaretPosition(0);
			while(findForward()) {
				editorPane.replaceSelection(replaceString);	
			}
		}
		else {
			if(findForward) {
				while(findForward()) {
					editorPane.replaceSelection(replaceString);
					++count;	
				}
			}
			else {
				while(findBackward()) {
					editorPane.replaceSelection(replaceString);
					++count;
				}
			}
		}

		

		Vector v = new Vector();
		v.add("Replaced " + count + " occurrences of \"" + findString + "\" with \"" + replaceString + "\"");
		MainFrame.getMainFrame().setFeedbackListData(v);
	}
		
	/**
	 * Writes the data in the editing window out to disk, the file that it writes
	 * to is the same as it was at construction
	 * @throws IOException if there is a disk error
	 */
	public void write() throws IOException {
		makeValidForParser();
		FileWriter fw = new FileWriter(fileName);
		editorPane.write(fw);
		change = false;
		modifiedLabel.setText("");
		fw.close();
	}
	
	/**
	 * In order for the file to be valid for the parser
	 * there must be a newline following
	 */
	
	private void makeValidForParser() {
		String text = editorPane.getText();
		int pound = text.lastIndexOf("#");
		if(pound == -1) 
			return;
		int nl = text.lastIndexOf("\n");
		if(nl < pound) {
			text += "\n";
			editorPane.setText(text);
		}
	}
	
	/**
	 * Reverts the contents of the editor to it's saved copy
	 */
	public void revert() throws IOException {
		Reader theReader = new edu.umich.visualsoar.util.TabRemovingReader(new FileReader(fileName));

		editorPane.read(theReader);
		registerDocumentListeners();
		theReader.close();
		
		modifiedLabel.setText("");
		change = false;
		
		editorPane.colorSyntax();
	}	
	
	/**
	 * Hides the caret for the editor window
	 */
	public void hideCaret() {
		Caret c = editorPane.getCaret();
		c.setVisible(false);
	}
		
	/**
	 * @return returns the file that this window is associated with
	 */
	 public String getFile() {
	 	return fileName;
	 }
	 
	 public Vector parseProductions() throws ParseException {
	 	makeValidForParser();
	 	SoarParser parser = new SoarParser(new StringReader(getAllText()));
	 	return parser.VisualSoarFile();
	 }
	 
	 /**
	  * Recolors the syntax to reflect a color preference change
	  */
	 public void colorSyntax() {
	 	editorPane.colorSyntax();
	 }
	 
	/**
	 * The file underneath of us has been renamed
	 * @param newFileName what the new name is
	 */
	 public void fileRenamed(String newFileName) {
	 	setTitle(getNode().getUniqueName()); 
	 	fileName = newFileName;
	 }	 
	 
	/**
	 * This lays out the Rule Editor according to specifications
	 */
	 private void initLayout() {
	 	// Take care of the panel to the south
	 	JPanel southPanel = new JPanel(new BorderLayout());
		southPanel.add(lineNumberLabel,BorderLayout.WEST);
		southPanel.add(modifiedLabel,BorderLayout.EAST);

	 	// do the rest of the content pane
		Container contentPane = getContentPane();
		contentPane.setLayout(new BorderLayout());
		contentPane.add(new JScrollPane(editorPane));
		contentPane.add(southPanel, BorderLayout.SOUTH);
	 }
	 
	/**
	 * Initializes the menubar according to specifications
	 */
	private void initMenuBar() {
		JMenuBar menuBar = new JMenuBar();

		initFileMenu(menuBar);
		initEditMenu(menuBar);
		initSearchMenu(menuBar);
		initSoarMenu(menuBar);
		initSoarRuntimeMenu(menuBar);

		setJMenuBar(menuBar);
	 }

  	private void initMenuBarExternFile() {
		JMenuBar menuBar = new JMenuBar();

		initFileMenu(menuBar);
		initEditMenu(menuBar);
		initSearchMenu(menuBar);
		//initSoarMenu(menuBar);
		//initSoarRuntimeMenu(menuBar);

		setJMenuBar(menuBar);
	 }
	 
	private void initFileMenu(JMenuBar menuBar) {
		JMenu fileMenu = new JMenu("File");
		
		// Save Action
		JMenuItem saveItem = new JMenuItem("Save");
		saveItem.addActionListener(saveAction);
		saveItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S,Event.CTRL_MASK));
		fileMenu.add(saveItem);

		JMenuItem revertToSavedItem = new JMenuItem("Revert To Saved");
		revertToSavedItem.addActionListener(revertToSavedAction);
		//revertToSavedAction.addPropertyChangeListener(new ActionButtonAssociation(revertToSavedAction, revertToSavedItem));
		fileMenu.add(revertToSavedItem);
		
		JMenuItem closeItem = new JMenuItem("Close");
		closeItem.addActionListener(closeAction);
		fileMenu.add(closeItem);
		
		fileMenu.setMnemonic(KeyEvent.VK_I);
		menuBar.add(fileMenu);
	}
				
	private void initEditMenu(JMenuBar menuBar) {
		JMenu editMenu = new JMenu("Edit");
		JMenuItem undoItem = new JMenuItem("Undo");
		editMenu.add(undoItem);
		undoItem.addActionListener(undoAction);
		undoAction.addPropertyChangeListener(new ActionButtonAssociation(undoAction,undoItem));
		
		JMenuItem redoItem = new JMenuItem("Redo");
		editMenu.add(redoItem);
		redoItem.addActionListener(redoAction);
		redoAction.addPropertyChangeListener(new ActionButtonAssociation(redoAction,redoItem));
		
		editMenu.addMenuListener(new MenuAdapter() {
          	 public void menuSelected(MenuEvent e) {
          	 	undoAction.setEnabled(undoManager.canUndo());
          	 	redoAction.setEnabled(undoManager.canRedo());
          	 }
		});
		
		editMenu.addSeparator();
		JMenuItem commentOutItem = new JMenuItem("Comment Out");
		commentOutItem.addActionListener(commentOutAction);
		editMenu.add(commentOutItem);
		
		JMenuItem uncommentOutItem = new JMenuItem("Uncomment Out");
		uncommentOutItem.addActionListener(uncommentOutAction);
		editMenu.add(uncommentOutItem);
		
		editMenu.addSeparator();
		
		JMenuItem cutItem = new JMenuItem("Cut");
		cutItem.addActionListener(cutAction);
		editMenu.add(cutItem);
		
		JMenuItem copyItem = new JMenuItem("Copy");
		copyItem.addActionListener(copyAction);
		editMenu.add(copyItem);
		
		JMenuItem pasteItem = new JMenuItem("Paste");
		pasteItem.addActionListener(pasteAction);
		editMenu.add(pasteItem);
		
		editMenu.addSeparator();
		
		JMenuItem insertTextFromFileItem = new JMenuItem("Insert Text From File...");
		insertTextFromFileItem.addActionListener(insertTextFromFileAction);
		editMenu.add(insertTextFromFileItem);

    JMenuItem reDrawItem = new JMenuItem("Redraw Color Syntax");
    reDrawItem.addActionListener(reDrawAction);
    editMenu.add(reDrawItem);

    JMenuItem reJustifyItem = new JMenuItem("ReJustify Text");
    reJustifyItem.addActionListener(reJustifyAction);
    editMenu.add(reJustifyItem);

		// register accel and remember thingys
		editMenu.setMnemonic('E');
		undoItem.setMnemonic(KeyEvent.VK_D);
		undoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z,Event.CTRL_MASK));
		redoItem.setMnemonic(KeyEvent.VK_R);
		redoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z,Event.CTRL_MASK | Event.SHIFT_MASK));
		cutItem.setMnemonic(KeyEvent.VK_T);
		cutItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X,Event.CTRL_MASK));
		copyItem.setMnemonic(KeyEvent.VK_C);
		copyItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C,Event.CTRL_MASK));
		pasteItem.setMnemonic(KeyEvent.VK_P);
		pasteItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V,Event.CTRL_MASK));
    reDrawItem.setMnemonic(KeyEvent.VK_D);
    reDrawItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_D,Event.CTRL_MASK));
    reJustifyItem.setMnemonic(KeyEvent.VK_J);
    reJustifyItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_J,Event.CTRL_MASK));
		
		menuBar.add(editMenu);
	}
	
	private void initSearchMenu(JMenuBar menuBar) {		
		JMenu searchMenu = new JMenu("Search");
		
		JMenuItem findItem = new JMenuItem("Find...");
		findItem.addActionListener(findAction);
		searchMenu.add(findItem);
		
		JMenuItem findAgainItem = new JMenuItem("Find Again");
		findAgainItem.addActionListener(findAgainAction);
		findAgainAction.addPropertyChangeListener(new ActionButtonAssociation(findAgainAction,findAgainItem));

		searchMenu.add(findAgainItem);
		
		JMenuItem findAndReplaceItem = new JMenuItem("Find & Replace...");
		findAndReplaceItem.addActionListener(findAndReplaceAction);
		searchMenu.add(findAndReplaceItem);
		
		JMenuItem replaceItem = new JMenuItem("Replace");
		replaceItem.addActionListener(replaceAction);
		replaceAction.addPropertyChangeListener(new ActionButtonAssociation(replaceAction,replaceItem));
		searchMenu.add(replaceItem);
		
		JMenuItem replaceAndFindAgainItem = new JMenuItem("Replace & Find Again");
		replaceAndFindAgainItem.addActionListener(replaceAndFindAgainAction);
		replaceAndFindAgainAction.addPropertyChangeListener(new ActionButtonAssociation(replaceAndFindAgainAction,replaceAndFindAgainItem));
		searchMenu.add(replaceAndFindAgainItem);
		
		JMenuItem replaceAllItem = new JMenuItem("Replace All");
		replaceAllItem.addActionListener(replaceAllAction);
		replaceAllAction.addPropertyChangeListener(new ActionButtonAssociation(replaceAllAction,replaceAllItem));
		searchMenu.add(replaceAllItem);
		
		// Register accel and mnemonics
		searchMenu.setMnemonic('S');
		findItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F,Event.CTRL_MASK));
		findItem.setMnemonic(KeyEvent.VK_F);
		findAgainItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_G,Event.CTRL_MASK));
		findAndReplaceItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R,Event.CTRL_MASK));
		replaceItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS,Event.CTRL_MASK));
		replaceAndFindAgainItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_H,Event.CTRL_MASK));
		
		//findAgainItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F3,0));
		//findAgainItem.setMnemonic(KeyEvent.VK_A);
		
		menuBar.add(searchMenu);
	}
	
	// 3P - Changes to this function were made to add the STI related menu items.
	//
	// Update, those changes have since been moved to CreateSoarRuntimeMenu.
	private void initSoarMenu(JMenuBar menuBar) {
		///////////////////////////////////////
		// Soar menu
		JMenu soarMenu = new JMenu("Soar");
				
		// "Check Productions" menu item
		JMenuItem checkProductionsItem = new JMenuItem("Check Productions Against Datamap");
		checkProductionsItem.addActionListener(checkProductionsAction);
		soarMenu.add(checkProductionsItem);
		
		checkProductionsItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F7,0));
		checkProductionsItem.setMnemonic(KeyEvent.VK_P);
		
		// "Soar Complete" menu item
		JMenuItem tabCompleteItem = new JMenuItem("Soar Complete");
		tabCompleteItem.addActionListener(tabCompleteAction);
		soarMenu.add(tabCompleteItem);
		
		tabCompleteItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,Event.CTRL_MASK));
		
		///////////////////////////////////////
		// Insert Template menu
		JMenu templates = new JMenu("Insert Template");
		Iterator i = MainFrame.getMainFrame().getTemplateManager().getTemplateNames();
		while(i.hasNext()) {
			String templateName = (String)i.next();
			JMenuItem currentTemplateItem = new JMenuItem(templateName);
			currentTemplateItem.addActionListener(new InsertTemplateAction(templateName));
			templates.add(currentTemplateItem);
		}
				
		soarMenu.setMnemonic(KeyEvent.VK_O);
		
		menuBar.add(soarMenu);
		menuBar.add(templates);
	}
	 
	// 3P
	// Initializes the "Runtime" menu item and adds it to the given menubar
	private void initSoarRuntimeMenu(JMenuBar menuBar)
	{
		// Create the Runtime menu
		JMenu runtimeMenu=new JMenu("Runtime");
		
		// "Send Production" menu item
		JMenuItem sendProductionToSoarItem = new JMenuItem("Send Production");
		sendProductionToSoarItem.addActionListener(sendProductionToSoarAction);
		runtimeMenu.add(sendProductionToSoarItem);
		
		// "Send File" menu item
		JMenuItem sendFileToSoarItem = new JMenuItem("Send File");
		sendFileToSoarItem.addActionListener(sendFileToSoarAction);
		runtimeMenu.add(sendFileToSoarItem);
		
		// "Matches" menu item
		JMenuItem matchesItem = new JMenuItem("Matches Production");
		matchesItem.addActionListener(sendMatchesToSoarAction);
		runtimeMenu.add(matchesItem);
		
		// "Excise Production" menu item
		JMenuItem exciseProductionItem = new JMenuItem("Excise Production");
		exciseProductionItem.addActionListener(sendExciseProductionToSoarAction);
		runtimeMenu.add(exciseProductionItem);

		// Set the mnemonic and add the menu to the menu bar		
		runtimeMenu.setMnemonic('R');
		menuBar.add(runtimeMenu);
	}
	
	/**
	 * This class is meant to catch if the user closes an internal frame without saving
	 * the file, it prompts them to save, or discard the file or cancel the close
	 */
	class CloseListener implements VetoableChangeListener {
		public void vetoableChange(PropertyChangeEvent e) throws PropertyVetoException {
			String name = e.getPropertyName();
			if (name.equals(JInternalFrame.IS_CLOSED_PROPERTY)) {
				Component internalFrame = (Component)e.getSource();

				// note we need to check this or else when the property is vetoed
				// the option pane will come up twice see Graphic Java 2 Volume II pg 889
				// for more information
				Boolean oldValue = (Boolean)e.getOldValue(),
						newValue = (Boolean)e.getNewValue();
				if (oldValue == Boolean.FALSE && newValue == Boolean.TRUE && change) {
					int answer = JOptionPane.showConfirmDialog(internalFrame,"Save Changes to " + fileName + "?",
									   "Unsaved Changes",JOptionPane.YES_NO_CANCEL_OPTION);
					if (answer == JOptionPane.CANCEL_OPTION) {
						throw new PropertyVetoException("close cancelled", e);
					} else if (answer == JOptionPane.YES_OPTION) {
						try {
							write();
						}
						catch(IOException ioe) {
							ioe.printStackTrace();
						}
					}
				}
			}
		}
	}

	////////////////////////////////////////////////////////
	// ACTIONS
	////////////////////////////////////////////////////////
	class InsertTextFromFileAction extends AbstractAction {
		public InsertTextFromFileAction() {
			super("Insert Text From File");
		}
		
		public void actionPerformed(ActionEvent event) {
			JFileChooser fileChooser = new JFileChooser();
			if(JFileChooser.APPROVE_OPTION == fileChooser.showOpenDialog(MainFrame.getMainFrame())) {
				try {
					Reader r = new FileReader(fileChooser.getSelectedFile());
					StringWriter w = new StringWriter();
					for(int rc = r.read(); rc != -1; w.write(rc), rc = r.read());
					editorPane.insert(w.toString(),editorPane.getCaret().getDot());
				}
				catch(IOException ioe) {
					JOptionPane.showMessageDialog(MainFrame.getMainFrame(),"There was an error inserting the text","Error",JOptionPane.ERROR_MESSAGE ); 
          
					
				}
			}
		}
	}
	
	/**
	 * Gets the currently selected rule editor and tells it to save itself 
	 */
	class SaveAction extends AbstractAction {
		public SaveAction() {
			super("Save File");
		}
		
		public void actionPerformed(ActionEvent event) {
			try {
				write();
			}
			catch(java.io.IOException ioe) { 
				ioe.printStackTrace();
			}
		}
	}
	
	/**
	 * reverts the editor's contents to it's last saved state
	 */
	class RevertToSavedAction extends AbstractAction {
		public RevertToSavedAction() {
			super("Revert To Saved");
			setEnabled(true);
		}
		
		public void actionPerformed(ActionEvent event) {
			try {
				revert();
			}
			catch(java.io.IOException ioe) { 
				ioe.printStackTrace();
			}
		}
	}
	
	/**
	 * Closes the current window
	 */
	class CloseAction extends AbstractAction {
		public CloseAction() {
			super("Close");
		}
		
		public void actionPerformed(ActionEvent event) {
			try {
				setClosed(true);

			}
			catch(PropertyVetoException pve) {
				// This is not an error
			}
      if(Preferences.getInstance().isAutoTilingEnabled())
        MainFrame.getMainFrame().getDesktopPane().performTileAction();
		}
	}
	
	class UndoAction extends AbstractAction {
		public UndoAction() {
			super("Undo");
		}
		
		public void actionPerformed(ActionEvent e) {
			if(undoManager.canUndo())
				undoManager.undo();
			else
				getToolkit().beep();
		}
	}

  class ReDrawAction extends AbstractAction {
    public ReDrawAction() {
      super("Redraw");
    }

    public void actionPerformed(ActionEvent e) {
      editorPane.colorSyntax();
    }
  }

  class ReJustifyAction extends AbstractAction {
    public ReJustifyAction() {
      super("ReJustify");
    }

    public void actionPerformed(ActionEvent e) {
      editorPane.justifyDocument();
    }
  }


	class RedoAction extends AbstractAction {
		public RedoAction() {
			super("Redo");
		}
		
		public void actionPerformed(ActionEvent e) {
			if(undoManager.canRedo())
				undoManager.redo();
			else
				getToolkit().beep();
		}
	}
	
	class FindAction extends AbstractAction {
		public FindAction() {
			super("Find");
		}
		
		public void actionPerformed(ActionEvent e) {
			FindDialog findDialog = new FindDialog(MainFrame.getMainFrame(),RuleEditor.this);
			findDialog.setVisible(true);
		}
	}
		
	class FindAndReplaceAction extends AbstractAction {
		public FindAndReplaceAction() {
			super("Find And Replace");
		}
		
		public void actionPerformed(ActionEvent e) {
			FindReplaceDialog findReplaceDialog = new FindReplaceDialog(MainFrame.getMainFrame(),RuleEditor.this);
			findReplaceDialog.setVisible(true);
		}
	}
	
	class FindAgainAction extends AbstractAction {
	
		public FindAgainAction() {
			super("Find Again");
			setEnabled(false);
		}
		
		public void actionPerformed(ActionEvent e) {
			find();
		}
	}
	
	class ReplaceAndFindAgainAction extends AbstractAction {
	
		public ReplaceAndFindAgainAction() {
			super("Replace & Find Again");
			setEnabled(false);
		}
		
		public void actionPerformed(ActionEvent e) {
			replace();
			find();
		}
	}
	
	class ReplaceAction extends AbstractAction {
	
		public ReplaceAction() {
			super("Replace");
			setEnabled(false);
		}
		
		public void actionPerformed(ActionEvent e) {
			replace();
		}
	}

	class ReplaceAllAction extends AbstractAction {
	
		public ReplaceAllAction() {
			super("Replace All");
			setEnabled(false);
		}
		
		public void actionPerformed(ActionEvent e) {
			replaceAll();
		}
	}

	class CheckProductionsAction extends AbstractAction {
		public CheckProductionsAction() {
			super("Check Productions");
		}
		
		public void actionPerformed(ActionEvent ae) {
			try {
				java.util.List errors = new LinkedList();
				Vector v = parseProductions();
				MainFrame.getMainFrame().getOperatorWindow().checkProductions((OperatorNode)associatedNode.getParent(),v,errors);
				Vector vecErrors = new Vector();
				if (errors.isEmpty())
					vecErrors.add("No errors detected in " + getFile());
				else {
					Enumeration e = new EnumerationIteratorWrapper(errors.iterator());
					while(e.hasMoreElements()) {
						try {
							String errorString = e.nextElement().toString();
							String numberString = errorString.substring(errorString.indexOf("(")+1,errorString.indexOf(")"));
							vecErrors.add(new FeedbackListObject(associatedNode,Integer.parseInt(numberString),errorString,true));
						}
						catch(NumberFormatException nfe) {
							System.out.println("Never happen");
						}
					}
				}
				MainFrame.getMainFrame().setFeedbackListData(vecErrors);
			}
			catch(ParseException pe) {
				JOptionPane.showMessageDialog(MainFrame.getMainFrame(), pe.getMessage(), "Parse Error", JOptionPane.ERROR_MESSAGE);
			}
		}
	}
	
	/**
	 * This class puts the instantiated template in the text area.
	 */
	class InsertTemplateAction extends AbstractAction {
		private String d_templateName;
	
		// NOT IMPLEMENTED
		private InsertTemplateAction() {}
	
	
		public InsertTemplateAction(String templateName) {
			super(templateName);
			d_templateName = templateName;
		}
	
		public void actionPerformed(ActionEvent e) {
			try {
				TemplateManager tm = MainFrame.getMainFrame().getTemplateManager();
				editorPane.insert(tm.instantiate(d_templateName,associatedNode),editorPane.getCaret().getDot());
			}	
			catch(TemplateInstantiationException tie) {
				JOptionPane.showMessageDialog(RuleEditor.this,tie.getMessage(),"Template Error",JOptionPane.ERROR_MESSAGE);
			}		
		}
	}
	
	/**
	 * This class comments (puts a # in the first position for every line) for the currently selected text
	 * of the text area
	 */
	class CommentOutAction extends AbstractAction {
		public CommentOutAction() {
			super("Comment Out");
		}
	
		public void actionPerformed(ActionEvent e) {
			String selectedText = editorPane.getSelectedText();
			if(selectedText != null) {
				String commentText = "#" + selectedText;
				int nl = commentText.indexOf('\n');
				while(nl != -1) {
					commentText = commentText.substring(0,nl+1) + "#" + commentText.substring(nl+1,commentText.length());
					nl = (nl+1) >= commentText.length() ? -1 : commentText.indexOf('\n',nl+1);
				}
				
				editorPane.replaceRange(commentText,editorPane.getSelectionStart(),editorPane.getSelectionEnd());
			}
			else {
				getToolkit().beep();
			}
		}
	}
	
	/**
	 * This class uncomments (takes out the # in the first position for every line) from the currently selected
	 * text of the text area.
	 */
	class UncommentOutAction extends AbstractAction {
		public UncommentOutAction() {
			super("Uncomment Out");
		}
		
		public void actionPerformed(ActionEvent e) {
			String selectedText = editorPane.getSelectedText();
			if(selectedText != null) {
				String uncommentText = selectedText;
				if(uncommentText.charAt(0) == '#')
					uncommentText = uncommentText.substring(1,uncommentText.length());
				int nlp = uncommentText.indexOf("\n#");
				while(nlp != -1) {
					uncommentText = uncommentText.substring(0,nlp+1) + uncommentText.substring(nlp+2,uncommentText.length());
					nlp = uncommentText.indexOf("\n#",nlp+1);
				}
				
				editorPane.replaceRange(uncommentText,editorPane.getSelectionStart(),editorPane.getSelectionEnd());
			}
			else {
				getToolkit().beep();
			}
		}
	}


  /**
   *  A simplified version of the TabCompleteAction that only displays
   *  the next possible attribute in the feedback window following the user
   *  typing a dot/period.
   *  Unlike the TabCompleteAction, this action does not ever insert anything
   *  into the rule editor, it only displays the attribute options in the feedback window.
   */
  class AutoSoarCompleteAction extends AbstractAction {
    public AutoSoarCompleteAction() {
      super("Auto Soar Complete");
    }

    public void actionPerformed(ActionEvent e) {
      // Do character insertion and caret adjustment stuff
      SoarDocument 	doc = (SoarDocument)editorPane.getDocument();
			String textTyped = e.getActionCommand();
      int caretPos = editorPane.getCaretPosition();

			if (textTyped.equals("\n")) {
				(new DefaultEditorKit.InsertBreakAction()).actionPerformed(e);
				caretPos++;
			}
			else if (! textTyped.equals("\t")) {
				(new DefaultEditorKit.DefaultKeyTypedAction()).actionPerformed(e);
				caretPos++;
			}			

      caretPos = doc.autoJustify(caretPos);
      if (caretPos > 0) {
        editorPane.setCaretPosition(caretPos);
      }


      // Advanced Soar Complete stuff
			int pos = editorPane.getCaretPosition();
			String text = editorPane.getText();
			int sp_pos = text.lastIndexOf("sp ",pos);
			if(sp_pos == -1) {
				getToolkit().beep();
				return;
			}
			String prodSoFar = text.substring(sp_pos,pos);
			int arrowPos = prodSoFar.indexOf("-->");
			String end;
			if(arrowPos == -1)
				end = ") --> }";
			else
				end = " <$$$>)}";
			int caret = prodSoFar.lastIndexOf("^");
			int period = prodSoFar.lastIndexOf(".");
			int space = prodSoFar.lastIndexOf(" ");
			String userType = new String();


      // Guarantee that period is more relevant than space and caret
      if(period != -1 && caret != -1 && space != -1 && period > caret && period > space) {
        userType = prodSoFar.substring(period+1,prodSoFar.length());
        prodSoFar = prodSoFar.substring(0,period+1) + "<$$>" + end;
        attributeComplete(pos,userType,prodSoFar);
      }
			else {
				getToolkit().beep();
			}
    } // end of actionPerformed()

    /**
     *  uses the soar parser to generate all the possible attributes that can follow
     */
		private void attributeComplete(int pos,String userType,String prodSoFar) {
			try {
				prodSoFar = validForParser(prodSoFar);
				SoarParser soarParser = new SoarParser(new StringReader(prodSoFar));
				SoarProduction sp = soarParser.soarProduction();
				OperatorNode on = getNode();
				OperatorNode parent = (OperatorNode)on.getParent();
				java.util.List matches;
				SoarIdentifierVertex siv = ((OperatorNode)on.getParent()).getStateIdVertex();
				if(siv != null) {
					matches = MainFrame.getMainFrame().getOperatorWindow().getDatamap().matches(siv,sp,"<$$>");
				}
				else {
					SoarWorkingMemoryModel dataMap = MainFrame.getMainFrame().getOperatorWindow().getDatamap();
					matches = dataMap.matches(dataMap.getTopstate(),sp,"<$$>");
				}
				java.util.List completeMatches = new LinkedList();
				Iterator i = matches.iterator();
				while(i.hasNext()) {
					String matched = (String)i.next();
					if(matched.startsWith(userType)) {
						completeMatches.add(matched);
					}
				}
				display(completeMatches);
			}
			catch(ParseException pe) {
				getToolkit().beep();
			}
		}   // end of attributeComplete()

    /**
     *  Displays all the possible attributes that can follow the dot/period to the
     *  feedback list.
     *  @param  completeMatches List of Strings representing possible attributes to be displayed
     */
		private void display(java.util.List completeMatches) {
			if(completeMatches.size() == 0) {
        getToolkit().beep();
			}
			else {
        MainFrame.getMainFrame().setFeedbackListData(new Vector(completeMatches));
      }
		}    // end of display()

		private String validForParser(String prod) {
			String text = editorPane.getText();
			int pound = text.lastIndexOf("#");
			int nl = text.lastIndexOf("\n");
			if(nl < pound && pound != -1) {
				prod += "\n";
			}	
			return prod;
		}    


  }  // end of AutoSoarCompleteAction


	
	class TabCompleteAction extends AbstractAction {
		public TabCompleteAction() {
			super("Tab Complete");
		}
	

		public void actionPerformed(ActionEvent e) {
			int pos = editorPane.getCaretPosition();
			String text = editorPane.getText();
			int sp_pos = text.lastIndexOf("sp ",pos);
			if(sp_pos == -1) {
				getToolkit().beep();
				return;
			}
			String prodSoFar = text.substring(sp_pos,pos);
			int arrowPos = prodSoFar.indexOf("-->");
			String end;
			if(arrowPos == -1)
				end = ") --> }";
			else
				end = " <$$$>)}";
			int caret = prodSoFar.lastIndexOf("^");
			int period = prodSoFar.lastIndexOf(".");
			int space = prodSoFar.lastIndexOf(" ");
			String userType = new String();
			// The most relevant is the caret
			if((period == -1 && caret != -1 && space != -1 && caret > space) 
				|| (period != -1 && caret != -1 && space != -1 && period < caret && space < caret)) {
				userType = prodSoFar.substring(caret+1,prodSoFar.length());
				prodSoFar = prodSoFar.substring(0,caret+1) + "<$$>" + end;
				attributeComplete(pos,userType,prodSoFar);
			}
			// The most relevant is the period
			else if(period != -1 && caret != -1 && space != -1 && period > caret && period > space) {
				userType = prodSoFar.substring(period+1,prodSoFar.length());
				prodSoFar = prodSoFar.substring(0,period+1) + "<$$>" + end;
				attributeComplete(pos,userType,prodSoFar);
			}
			// The most relevant is the space
			else if((period == -1 && caret != -1 && space != -1 && space > caret)
					|| (period != -1 && caret != -1 && space != -1 && space > caret && space > period)) {
				userType = prodSoFar.substring(space+1,prodSoFar.length());
				prodSoFar = prodSoFar.substring(0,space+1) + "<$$>" + end;
				valueComplete(pos,userType,prodSoFar);		
			}
			// Failure
			else {
				getToolkit().beep();
			}
		}
		
		private void valueComplete(int pos,String userType,String prodSoFar) {
			try {
				prodSoFar = validForParser(prodSoFar);
				SoarParser soarParser = new SoarParser(new StringReader(prodSoFar));
				SoarProduction sp = soarParser.soarProduction();
				OperatorNode on = getNode();
				OperatorNode parent = (OperatorNode)on.getParent();
				java.util.List matches;
				SoarIdentifierVertex siv = parent.getStateIdVertex();
				if(siv != null) {
					matches = MainFrame.getMainFrame().getOperatorWindow().getDatamap().matches(siv,sp,"<$$>");
				}
				else {
					SoarWorkingMemoryModel dataMap = MainFrame.getMainFrame().getOperatorWindow().getDatamap();
					matches = dataMap.matches(dataMap.getTopstate(),sp,"<$$>");
				}
				java.util.List completeMatches = new LinkedList();
				Iterator i = matches.iterator();
				while(i.hasNext()) {
					Object o = i.next();
					if(o instanceof EnumerationVertex) {
						EnumerationVertex ev = (EnumerationVertex)o;
						Iterator enum = ev.getEnumeration();
						while(enum.hasNext()) {
							String enumString = (String)enum.next();
							if(enumString.startsWith(userType)) {
								completeMatches.add(enumString);
							}
						}
					}	
				}
				complete(pos,userType,completeMatches);
			}
			catch(ParseException pe) {
				getToolkit().beep();
			}
		}


		private void complete(int pos,String userType,java.util.List completeMatches) {
			if(completeMatches.size() == 0) {
						getToolkit().beep();
			}
			else if(completeMatches.size() == 1) {
				String matched = (String)completeMatches.get(0);
				editorPane.insert(matched.substring(userType.length(),matched.length()),pos);
			}
			else {
				boolean stillGood = true;
				String addedCharacters = "";
				String matched = (String)completeMatches.get(0);
				int curPos = userType.length();
				while(stillGood && curPos < matched.length()) {
					String newAddedCharacters = addedCharacters + matched.charAt(curPos);
					String potStartString = userType + newAddedCharacters;
					Iterator j = completeMatches.iterator();
					while(j.hasNext()) {
						String currentString = (String)j.next();
						if(!currentString.startsWith(potStartString)) {
							stillGood = false;
							break;
						}
					}

					if(stillGood) {
						addedCharacters = newAddedCharacters;
						++curPos;
					}
				}
				editorPane.insert(addedCharacters,pos);
				MainFrame.getMainFrame().setFeedbackListData(new Vector(completeMatches));
				getToolkit().beep();
			}
		}
		
		private void attributeComplete(int pos,String userType,String prodSoFar) {
			try {
				prodSoFar = validForParser(prodSoFar);
				SoarParser soarParser = new SoarParser(new StringReader(prodSoFar));
				SoarProduction sp = soarParser.soarProduction();
				OperatorNode on = getNode();
				OperatorNode parent = (OperatorNode)on.getParent();
				java.util.List matches;
				SoarIdentifierVertex siv = ((OperatorNode)on.getParent()).getStateIdVertex();
				if(siv != null) {
					matches = MainFrame.getMainFrame().getOperatorWindow().getDatamap().matches(siv,sp,"<$$>");
				}
				else {
					SoarWorkingMemoryModel dataMap = MainFrame.getMainFrame().getOperatorWindow().getDatamap();
					matches = dataMap.matches(dataMap.getTopstate(),sp,"<$$>");
				}
				java.util.List completeMatches = new LinkedList();
				Iterator i = matches.iterator();
				while(i.hasNext()) {
					String matched = (String)i.next();
					if(matched.startsWith(userType)) {
						completeMatches.add(matched);
					}
				}
				complete(pos,userType,completeMatches);
			}
			catch(ParseException pe) {
				getToolkit().beep();
			}
		}
		
		private String validForParser(String prod) {
			String text = editorPane.getText();
			int pound = text.lastIndexOf("#");
			int nl = text.lastIndexOf("\n");
			if(nl < pound && pound != -1) {
				prod += "\n";
			}	
			return prod;
		}
	}

	// 3P
	// Handles the "Runtime|Send Production" menu item
	class SendProductionToSoarAction extends AbstractAction
	{
		public SendProductionToSoarAction()
		{
			super("Send Production");
		}
	
		public void actionPerformed(ActionEvent e)
		{
			// Get the production string that our caret is over
			String sProductionString=GetProductionStringUnderCaret();
			if (sProductionString == null)
			{
				getToolkit().beep();			
				return;
			}
			
			// Get the STI
			SoarToolJavaInterface soarToolInterface=MainFrame.getMainFrame().GetSoarToolJavaInterface();
			if (soarToolInterface == null)
			{
				JOptionPane.showMessageDialog(RuleEditor.this,"Soar Tool Interface is not initialized.","Error",JOptionPane.ERROR_MESSAGE);	
				return;
			}
			
			// Send the production through the STI
			String sProductionName=GetProductionNameUnderCaret();
			if (sProductionName != null)
			{
				soarToolInterface.SendProduction(sProductionName, sProductionString);
			}
		}
	}
	
	// 3P
	// Handles the "Runtime|Send File" menu item
	class SendFileToSoarAction extends AbstractAction
	{
		public SendFileToSoarAction()
		{
			super("Send File");
		}
	
		public void actionPerformed(ActionEvent e)
		{
			// Get the STI
			SoarToolJavaInterface soarToolInterface=MainFrame.getMainFrame().GetSoarToolJavaInterface();
			if (soarToolInterface == null)
			{
				JOptionPane.showMessageDialog(RuleEditor.this,"Soar Tool Interface is not initialized.","Error",JOptionPane.ERROR_MESSAGE);	
				return;
			}

			// Save the file
			try
			{
				write();
			}
			catch(java.io.IOException ioe)
			{ 
				ioe.printStackTrace();
			}
			
			// Send the file through the STI
			soarToolInterface.SendFile(fileName);
		}
	}

	// 3P
	// Handles the "Runtime|Matches Production" menu item
	class SendMatchesToSoarAction extends AbstractAction
	{
		public SendMatchesToSoarAction()
		{
			super("Matches Production");
		}
	
		public void actionPerformed(ActionEvent e)
		{
			// Get the STI
			SoarToolJavaInterface soarToolInterface=MainFrame.getMainFrame().GetSoarToolJavaInterface();
			if (soarToolInterface == null)
			{
				JOptionPane.showMessageDialog(RuleEditor.this,"Soar Tool Interface is not initialized.","Error",JOptionPane.ERROR_MESSAGE);	
				return;
			}

			// Call matches in Soar through STI
			String sProductionName=GetProductionNameUnderCaret();
			if (sProductionName != null)
			{			
				soarToolInterface.SendProductionMatches(sProductionName);
			}
		}
	}

	// 3P
	// Handles the "Runtime|Excise Production" menu item
	class SendExciseProductionToSoarAction extends AbstractAction
	{
		public SendExciseProductionToSoarAction()
		{
			super("Excise Production");
		}
	
		public void actionPerformed(ActionEvent e)
		{
			// Get the STI
			SoarToolJavaInterface soarToolInterface=MainFrame.getMainFrame().GetSoarToolJavaInterface();
			if (soarToolInterface == null)
			{
				JOptionPane.showMessageDialog(RuleEditor.this,"Soar Tool Interface is not initialized.","Error",JOptionPane.ERROR_MESSAGE);	
				return;
			}
			
			// Call excise in Soar through STI
			String sProductionName=GetProductionNameUnderCaret();
			if (sProductionName != null)
			{
				soarToolInterface.SendExciseProduction(sProductionName);
			}
		}
	}
	 
	class BackupThread extends Thread {
		Runnable writeOutControl;
		boolean closed = false;
	
		public BackupThread() {
			writeOutControl = new Runnable() {
				public void run() {
					if(!isClosed()) {
						String modifiedLabelText = modifiedLabel.getText();
						modifiedLabel.setText("AutoSaving...");
						makeValidForParser();
						try {
							FileWriter fw = new FileWriter(fileName+"~");
							editorPane.write(fw);
							fw.close();
						}
						catch(IOException ioe) {}
						modifiedLabel.setText(modifiedLabelText);
					}
					else {
						closed = true;
					}
				}
			};

		}
	
		public void run() {
			try {
				while(!closed) {
					// 3 minutes
					sleep(60000*3);
					SwingUtilities.invokeAndWait(writeOutControl);
				}
			}
			catch(InterruptedException ie) {
			}
			catch(java.lang.reflect.InvocationTargetException ite) {
			
			}
		}
	
	}
	
	class IgnoreSyntaxColorUndoManager extends UndoManager {
		public boolean addEdit(UndoableEdit anEdit) {
			if(!anEdit.getPresentationName().equals("style change")) {
				return super.addEdit(anEdit);
			}
			else {
				return false;
			}
			
		}
           
	}


}
