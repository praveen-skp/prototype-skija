/*******************************************************************************
 * Copyright (c) 2000, 2016 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.widgets;

import java.util.*;

import org.eclipse.swt.*;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.*;
import org.eclipse.swt.internal.win32.*;

/**
 * Instances of this class represent a selectable user interface object that
 * displays a list of strings and issues notification when a string is selected.
 * A list may be single or multi select.
 * <dl>
 * <dt><b>Styles:</b></dt>
 * <dd>SINGLE, MULTI</dd>
 * <dt><b>Events:</b></dt>
 * <dd>Selection, DefaultSelection</dd>
 * </dl>
 * <p>
 * Note: Only one of SINGLE and MULTI may be specified.
 * </p>
 * <p>
 * IMPORTANT: This class is <em>not</em> intended to be subclassed.
 * </p>
 *
 * @see <a href="http://www.eclipse.org/swt/snippets/#list">List snippets</a>
 * @see <a href="http://www.eclipse.org/swt/examples.php">SWT Example:
 *      ControlExample</a>
 * @see <a href="http://www.eclipse.org/swt/">Sample code and further
 *      information</a>
 * @noextend This class is not intended to be subclassed by clients.
 */
public class List extends Scrollable implements ICustomWidget {
	static final int INSET = 3;
	static final long ListProc;
	static final TCHAR ListClass = new TCHAR(0, "LISTBOX", true);
	boolean addedUCC = false; // indicates whether Bidi UCC were added; 'state &
								// HAS_AUTO_DIRECTION' isn't a sufficient
								// indicator
	static {
		WNDCLASS lpWndClass = new WNDCLASS();
		OS.GetClassInfo(0, ListClass, lpWndClass);
		ListProc = lpWndClass.lpfnWndProc;
		DPIZoomChangeRegistry.registerHandler(List::handleDPIChange, List.class);
	}

	java.util.List<String> lines = new ArrayList<>();
	java.util.List<Integer> selectedLines = new ArrayList<>();

	private int topIndex = 0;
	private Integer lastSelectedItem = 0;
	private Listener listener;
	private boolean hasMouseEntered;

	/**
	 * Constructs a new instance of this class given its parent and a style value
	 * describing its behavior and appearance.
	 * <p>
	 * The style value is either one of the style constants defined in class
	 * <code>SWT</code> which is applicable to instances of this class, or must be
	 * built by <em>bitwise OR</em>'ing together (that is, using the
	 * <code>int</code> "|" operator) two or more of those <code>SWT</code> style
	 * constants. The class description lists the style constants that are
	 * applicable to the class. Style bits are also inherited from superclasses.
	 * </p>
	 *
	 * @param parent a composite control which will be the parent of the new
	 *               instance (cannot be null)
	 * @param style  the style of control to construct
	 *
	 * @exception IllegalArgumentException
	 *                                     <ul>
	 *                                     <li>ERROR_NULL_ARGUMENT - if the parent
	 *                                     is null</li>
	 *                                     </ul>
	 * @exception SWTException
	 *                                     <ul>
	 *                                     <li>ERROR_THREAD_INVALID_ACCESS - if not
	 *                                     called from the thread that created the
	 *                                     parent</li>
	 *                                     <li>ERROR_INVALID_SUBCLASS - if this
	 *                                     class is not an allowed subclass</li>
	 *                                     </ul>
	 *
	 * @see SWT#SINGLE
	 * @see SWT#MULTI
	 * @see Widget#checkSubclass
	 * @see Widget#getStyle
	 */
	public List(Composite parent, int style) {
		super(parent, checkStyle(style));
		addListeners();
		showScrollBar();
	}

	private void showScrollBar() {
		if (verticalBar != null) {
			verticalBar.setVisible(true);
		}
		if (horizontalBar != null) {
			horizontalBar.setVisible(true);
		}
	}

	private void addListeners() {
		listener = event -> {
			switch (event.type) {
			case SWT.Dispose:
				onDispose(event);
				break;
			case SWT.MouseDown:
				onMouseDown(event);
				break;
			case SWT.MouseUp:
				onMouseUp(event);
				break;
			case SWT.Paint:
				onPaint(event);
				break;
			case SWT.Resize:
				onResize();
				break;
			case SWT.FocusIn:
				onFocusIn();
				break;
			case SWT.FocusOut:
				onFocusOut();
				break;
			case SWT.Traverse:
				onTraverse(event);
				break;
			case SWT.Selection:
				onSelection(event);
				break;
			}
		};
		addListener(SWT.Dispose, listener);
		addListener(SWT.MouseDown, listener);
		addListener(SWT.MouseUp, listener);
		addListener(SWT.Paint, listener);
		addListener(SWT.Resize, listener);
		addListener(SWT.KeyDown, listener);
		addListener(SWT.FocusIn, listener);
		addListener(SWT.FocusOut, listener);
		addListener(SWT.Traverse, listener);
		addListener(SWT.Selection, listener);

		ScrollBar horizontalBar = getHorizontalBar();
		if (horizontalBar != null) {
			horizontalBar.addSelectionListener(new SelectionAdapter() {

				@Override
				public void widgetSelected(SelectionEvent e) {
					super.widgetSelected(e);
					List.this.scrollBarSelectionChanged(e);
				}
			});
		}
		ScrollBar verticalBar = getVerticalBar();
		if (verticalBar != null) {
			verticalBar.addSelectionListener(new SelectionAdapter() {

				@Override
				public void widgetSelected(SelectionEvent e) {
					super.widgetSelected(e);
					List.this.topIndex = verticalBar.getSelection();
					List.this.scrollBarSelectionChanged(e);
				}
			});
		}

		addKeyListener(new KeyListener() {
			@Override
			public void keyReleased(KeyEvent e) {
				onKeyReleased(e);
			}

			@Override
			public void keyPressed(KeyEvent e) {
				onKeyPressed(e);
			}
		});

		addMouseTrackListener(new MouseTrackAdapter() {

			@Override
			public void mouseEnter(MouseEvent e) {
				if (!hasMouseEntered) {
					hasMouseEntered = true;
					System.out.println("Mouse is at at: " + e.x + " " + e.y);
					redraw();
				}
			}

			@Override
			public void mouseExit(MouseEvent e) {
				hasMouseEntered = false;
				redraw();
			}

		});
	}

	private void updateScrollBarWithTextSize() {
		Rectangle clientArea = getClientArea();
		int height = clientArea.height;
		int thumb = height / getLineHeight();
		verticalBar.setMaximum(this.lines.size());
		verticalBar.setMinimum(0);
		verticalBar.setThumb(thumb);

		Point maxTextSize = computeTextSize();
		horizontalBar.setThumb(clientArea.width / maxTextSize.x);
		horizontalBar.setMaximum(maxTextSize.x);
		horizontalBar.setMinimum(0);
//		if (verticalBar != null) {
//			if (maxTextSize.y > clientArea.height) {
//				verticalBar.setVisible(true);
//				verticalBar.setMaximum(maxTextSize.y);
//			} else {
//				verticalBar.setVisible(false);
//			}
//		}
//		if (horizontalBar != null) {
//			if (maxTextSize.x > clientArea.width) {
//				horizontalBar.setMaximum(maxTextSize.x);
//				horizontalBar.setIncrement(10);
//				horizontalBar.setVisible(true);
//			} else {
//				horizontalBar.setVisible(false);
//			}
//		}

	}

	public int getLineHeight() {
		checkWidget();
		GC gc = new GC(this);
		int height = getLineHeight(gc);
		gc.dispose();
		return height;
	}

	private Point computeTextSize() {
		GC gc = new GC(this);
		gc.setFont(getFont());
		int width = 0, height = 0;
		if ((style & SWT.SINGLE) != 0) {
			String str = this.lines.get(0);
			Point size = gc.textExtent(str);
			if (str.length() > 0) {
				width = (int) Math.ceil(size.x);
			}
			height = (int) Math.ceil(size.y);
		} else {
			Point size = null;
			for (String line : this.lines) {
				size = gc.textExtent(line);
				width = Math.max(width, size.x);
			}
			height = size.y * this.lines.size();
			if (horizontalBar != null) {
				height += horizontalBar.getSize().y;
			}
			if (verticalBar != null) {
				width += verticalBar.getSize().x;
			}
		}
		gc.dispose();

		return new Point(width, height);

	}

	private void scrollBarSelectionChanged(SelectionEvent e) {
		redraw();
	}

	private void handleSelection() {
		sendSelectionEvent(SWT.Selection);
	}

	private void onMouseUp(Event e) {
		// Handle left mouse button clicks
		if ((e.stateMask & SWT.BUTTON1) != 0) {
			if ((e.stateMask & SWT.CTRL) != 0) {
				// Handle Ctrl + Click for multi-selection
				handleCtrlClick(e.x, e.y);
			} else {
				toggleSelectedLine(e);
			}
		}
		redraw();
	}

	private void handleCtrlClick(int x, int y) {
		// Determine the clicked line based on mouse coordinates
		int clickedLine = getTextLocation(x, y);

		if (clickedLine >= 0 && clickedLine < this.lines.size()) {
			System.out.println("Selected lines: " + this.selectedLines.toString());
			System.out.println("Clicked Line: " + clickedLine);
			if (this.selectedLines.contains(clickedLine)) {
				this.selectedLines.remove(Integer.valueOf(clickedLine));
			} else {
				System.out.println();
				this.selectedLines.add(clickedLine);
				this.lastSelectedItem = clickedLine;
			}
			System.out.println("New Selected lines: " + this.selectedLines.toString());
		}
	}

	private void onSelection(Event event) {
		redraw();
	}

	private void onTraverse(Event event) {
	}

	private void onFocusIn() {
		redraw();
	}

	private void onFocusOut() {
		redraw();
	}

	private void onKeyPressed(KeyEvent event) {
	}

	private void onKeyReleased(KeyEvent event) {
		// Handle Shift + Arrow
		if ((event.stateMask & SWT.SHIFT) != 0) {
			handleArrowKeys(event.keyCode, true);
		}
		// Handle Arrow movement without modifiers
		else if (event.stateMask == 0) {
			handleArrowKeys(event.keyCode, false);
		}
	}

	private void handleArrowKeys(int keyCode, boolean isShiftPressed) {
		switch (keyCode) {
		case SWT.ARROW_DOWN:
			if (isShiftPressed) {
				selectMultipleLine(1);
			} else {
				moveSelectedLine(1);
			}
			redraw();
			break;
		case SWT.ARROW_UP:
			if (isShiftPressed) {
				selectMultipleLine(-1);
			} else {
				moveSelectedLine(-1);
			}
			redraw();
			break;
		default:
			break;
		}
	}

	private void selectMultipleLine(int offset) {
		int newIndex = calculateNewIndex(this.lastSelectedItem, offset);
		System.out.println("Select Multiple:");
		System.out.println("New Index:" + newIndex);
		if (this.selectedLines.contains(newIndex)) {
			this.selectedLines.remove(Integer.valueOf(newIndex - offset));
			this.lastSelectedItem = newIndex;
		} else {
			this.selectedLines.add(newIndex);
			this.lastSelectedItem = newIndex;
			System.out.println("Selected Lines: " + this.selectedLines.toString());
		}
	}

	private void moveSelectedLine(int offset) {
		if (this.selectedLines.size() == 1) {
			int currentIndex = this.selectedLines.get(0);
			int newIndex = calculateNewIndex(currentIndex, offset);
			this.selectedLines.set(0, newIndex);
		}
	}

	private int calculateNewIndex(int currentIndex, int offset) {
		int newIndex = currentIndex + offset;
		if (newIndex < 0) {
			newIndex = 0;
		} else if (newIndex > this.lines.size() - 1) {
			newIndex = this.lines.size() - 1;
		}
		return newIndex;
	}

	private void onResize() {
		redraw();
	}

	private void onPaint(Event event) {
		if (!isVisible()) {
			return;
		}

		GC gc = event.gc;
		if (gc == null) {
			gc = new GC(this);
			event.gc = gc;
		}

		doPaint(event);
		gc.dispose();
	}

	private Rectangle getVisibleArea() {
		Rectangle clientArea = getClientArea();

//		ScrollBar horizontalBar = getHorizontalBar();
//		ScrollBar verticalBar = getVerticalBar();
//
//		int hOffset = (horizontalBar != null) ? horizontalBar.getSelection() : 0;
//		int vOffset = (verticalBar != null) ? verticalBar.getSelection() : 0;
//
//		clientArea.x += hOffset;
//		clientArea.y += vOffset;

		return clientArea;
	}

	private void drawText(Event e, Rectangle visibleArea) {
		String[] lines = this.lines.toArray(new String[0]);
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i];
			drawTextLine(line, i, e.x, e.y, visibleArea, e.gc);
		}
	}

	private int getTextLocation(int selectedX, int selectedY) {
		Rectangle visibleArea = getVisibleArea();
		int y = Math.max(selectedY + visibleArea.y, 0);

		GC gc = new GC(this);
		String[] textLines = this.lines.toArray(new String[0]);
		int clickedLine = Math.min(Math.round(y / getLineHeight(gc)), textLines.length - 1);
		int selectedLine = Math.min(clickedLine, textLines.length - 1);
		return selectedLine;
	}

	private int getLineHeight(GC gc) {
		checkWidget();
		String str = this.lines.get(0);
		return gc.textExtent(str).y;
	}

	private void drawTextLine(String text, int lineNumber, int x, int y, Rectangle visibleArea, GC gc) {
		Point completeTextExtent = gc.textExtent(text);
		Rectangle clientArea = getClientArea();
		int _x;
		if ((style & SWT.CENTER) != 0) {
			_x = (clientArea.width - completeTextExtent.x) / 2;
		} else if ((style & SWT.RIGHT) != 0) {
			_x = clientArea.width - completeTextExtent.x;
		} else { // ((style & SWT.LEFT) != 0)
			_x = x;
		}
		_x -= visibleArea.x;
		int _y = y + lineNumber * completeTextExtent.y - visibleArea.y;
		if ((style & SWT.BORDER) != 0) {
			_x += getBorderWidth();
			_y += getBorderWidth();
		}
		// handle Vertical Scroll
		int sizeWithTopIndex = this.topIndex * completeTextExtent.y;
//		System.out.println("Y position " + _y + " sizeWithTop: " + sizeWithTopIndex);
		_y -= sizeWithTopIndex;
		// handle Horizontal Scroll
		int hSelection = getHorizontalBar().getSelection();
		_x -= hSelection;
//		adjustCanvasSize(gc);
		if (this.selectedLines.size() != 0 && this.selectedLines.contains(lineNumber)) {
			Color background = gc.getBackground();
			Color foreground = gc.getForeground();
			gc.setForeground(getDisplay().getSystemColor(SWT.COLOR_LIST_SELECTION_TEXT));
			gc.setBackground(getDisplay().getSystemColor(SWT.COLOR_LIST_SELECTION));
			gc.drawText(text, _x, _y);
			gc.setForeground(foreground);
			gc.setBackground(background);
		} else {
			gc.drawText(text, _x, _y);
		}
	}

//	@Override
//	ScrollBar createScrollBar(int type) {
//		ScrollBar bar = new ScrollBar(this, type);
//		if ((state & CANVAS) != 0) {
//			bar.setMaximum(100);
//			bar.setThumb(2);
//		}
//		return bar;
//	}

//	@Override
//	void createWidget() {
//		super.createWidget();
//		if ((style & SWT.H_SCROLL) != 0)
//			horizontalBar = createScrollBar(SWT.H_SCROLL);
//		if ((style & SWT.V_SCROLL) != 0)
//			verticalBar = createScrollBar(SWT.V_SCROLL);
//	}

	private void adjustCanvasSize(GC gc) {
		Point maxTextExtent = new Point(0, 0);

		for (String line : lines) {
			Point extent = gc.textExtent(line);
			maxTextExtent.x = Math.max(maxTextExtent.x, extent.x);
			maxTextExtent.y += extent.y;
		}

		ScrollBar verticalBar = getVerticalBar();
		ScrollBar horizontalBar = getHorizontalBar();

		if (verticalBar != null) {
			verticalBar.setMaximum(maxTextExtent.y);
		}
		if (horizontalBar != null) {
			horizontalBar.setMaximum(maxTextExtent.x);
		}

		// Adjust canvas size to fit content
		this.setSize(maxTextExtent.x + 20, maxTextExtent.y + 20); // Add padding
	}

	@Override
	public void setBounds(Rectangle rect) {
		super.setBounds(rect);
	}

	@Override
	public void setBounds(int x, int y, int width, int height) {
		super.setBounds(x, y, width, height);
	}

	private void doPaint(Event e) {
		Rectangle r = getBounds();
		if (r.width == 0 && r.height == 0) {
			return;
		}
		Rectangle visibleArea = getVisibleArea();
		drawText(e, visibleArea);
	}

//	private void drawBackground(Event e) {
//		GC gc = e.gc;
//		gc.fillRectangle(e.x, e.y, e.width - 1, e.height - 1);
//		if ((style & SWT.BORDER) != 0 && isEnabled()) {
//			Color foreground = gc.getForeground();
//			gc.setForeground(getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY));
//			gc.drawLine(e.x, e.y + e.height - 1, e.x + e.x + e.width - 1, e.y + e.height - 1);
//			gc.setForeground(foreground);
//		}
//	}

	private void onDispose(Event event) {
		this.dispose();
	}

	private void onMouseDown(Event e) {
		redraw();
	}

	private void toggleSelectedLine(Event e) {
		Integer selectedLine = Integer.valueOf(getTextLocation(e.x, e.y));
		this.selectedLines.clear();
		System.out.println("ToggleSelectedLine");
		this.selectedLines.add(selectedLine);
		this.lastSelectedItem = selectedLine;
	}

	/**
	 * Adds the argument to the end of the receiver's list.
	 * <p>
	 * Note: If control characters like '\n', '\t' etc. are used in the string, then
	 * the behavior is platform dependent.
	 * </p>
	 *
	 * @param string the new item
	 *
	 * @exception IllegalArgumentException
	 *                                     <ul>
	 *                                     <li>ERROR_NULL_ARGUMENT - if the string
	 *                                     is null</li>
	 *                                     </ul>
	 * @exception SWTException
	 *                                     <ul>
	 *                                     <li>ERROR_WIDGET_DISPOSED - if the
	 *                                     receiver has been disposed</li>
	 *                                     <li>ERROR_THREAD_INVALID_ACCESS - if not
	 *                                     called from the thread that created the
	 *                                     receiver</li>
	 *                                     </ul>
	 *
	 * @see #add(String,int)
	 */
	public void add(String string) {
		checkWidget();
		if (string == null)
			error(SWT.ERROR_NULL_ARGUMENT);

		this.lines.add(string);
		updateScrollBarWithTextSize();
		redraw();

	}

	/**
	 * Adds the argument to the receiver's list at the given zero-relative index.
	 * <p>
	 * Note: To add an item at the end of the list, use the result of calling
	 * <code>getItemCount()</code> as the index or use <code>add(String)</code>.
	 * </p>
	 * <p>
	 * Also note, if control characters like '\n', '\t' etc. are used in the string,
	 * then the behavior is platform dependent.
	 * </p>
	 *
	 * @param string the new item
	 * @param index  the index for the item
	 *
	 * @exception IllegalArgumentException
	 *                                     <ul>
	 *                                     <li>ERROR_NULL_ARGUMENT - if the string
	 *                                     is null</li>
	 *                                     <li>ERROR_INVALID_RANGE - if the index is
	 *                                     not between 0 and the number of elements
	 *                                     in the list (inclusive)</li>
	 *                                     </ul>
	 * @exception SWTException
	 *                                     <ul>
	 *                                     <li>ERROR_WIDGET_DISPOSED - if the
	 *                                     receiver has been disposed</li>
	 *                                     <li>ERROR_THREAD_INVALID_ACCESS - if not
	 *                                     called from the thread that created the
	 *                                     receiver</li>
	 *                                     </ul>
	 *
	 * @see #add(String)
	 */
	public void add(String string, int index) {
		checkWidget();
		if (string == null)
			error(SWT.ERROR_NULL_ARGUMENT);
		if (index == -1)
			error(SWT.ERROR_INVALID_RANGE);
		this.lines.add(index, string);
		redraw();
	}

	/**
	 * Adds the listener to the collection of listeners who will be notified when
	 * the user changes the receiver's selection, by sending it one of the messages
	 * defined in the <code>SelectionListener</code> interface.
	 * <p>
	 * <code>widgetSelected</code> is called when the selection changes.
	 * <code>widgetDefaultSelected</code> is typically called when an item is
	 * double-clicked.
	 * </p>
	 *
	 * @param listener the listener which should be notified when the user changes
	 *                 the receiver's selection
	 *
	 * @exception IllegalArgumentException
	 *                                     <ul>
	 *                                     <li>ERROR_NULL_ARGUMENT - if the listener
	 *                                     is null</li>
	 *                                     </ul>
	 * @exception SWTException
	 *                                     <ul>
	 *                                     <li>ERROR_WIDGET_DISPOSED - if the
	 *                                     receiver has been disposed</li>
	 *                                     <li>ERROR_THREAD_INVALID_ACCESS - if not
	 *                                     called from the thread that created the
	 *                                     receiver</li>
	 *                                     </ul>
	 *
	 * @see SelectionListener
	 * @see #removeSelectionListener
	 * @see SelectionEvent
	 */
	public void addSelectionListener(SelectionListener listener) {
		addTypedListener(listener, SWT.Selection, SWT.DefaultSelection);
	}

	static int checkStyle(int style) {
		return checkBits(style, SWT.SINGLE, SWT.MULTI, 0, 0, 0, 0);
	}

//	@Override
//	int defaultBackground() {
//		return OS.GetSysColor(OS.COLOR_WINDOW);
//	}

	/**
	 * Deselects the items at the given zero-relative indices in the receiver. If
	 * the item at the given zero-relative index in the receiver is selected, it is
	 * deselected. If the item at the index was not selected, it remains deselected.
	 * Indices that are out of range and duplicate indices are ignored.
	 *
	 * @param indices the array of indices for the items to deselect
	 *
	 * @exception IllegalArgumentException
	 *                                     <ul>
	 *                                     <li>ERROR_NULL_ARGUMENT - if the set of
	 *                                     indices is null</li>
	 *                                     </ul>
	 * @exception SWTException
	 *                                     <ul>
	 *                                     <li>ERROR_WIDGET_DISPOSED - if the
	 *                                     receiver has been disposed</li>
	 *                                     <li>ERROR_THREAD_INVALID_ACCESS - if not
	 *                                     called from the thread that created the
	 *                                     receiver</li>
	 *                                     </ul>
	 */
	public void deselect(int[] indices) {
		checkWidget();
		if (indices == null)
			error(SWT.ERROR_NULL_ARGUMENT);
		if (indices.length == 0)
			return;
		for (int index : indices) {
			deselect(index);
		}
	}

	/**
	 * Deselects the item at the given zero-relative index in the receiver. If the
	 * item at the index was already deselected, it remains deselected. Indices that
	 * are out of range are ignored.
	 *
	 * @param index the index of the item to deselect
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 */
	public void deselect(int index) {
		checkWidget();
		if (index == -1)
			return;
		this.selectedLines.remove(Integer.valueOf(index));
	}

	/**
	 * Deselects the items at the given zero-relative indices in the receiver. If
	 * the item at the given zero-relative index in the receiver is selected, it is
	 * deselected. If the item at the index was not selected, it remains deselected.
	 * The range of the indices is inclusive. Indices that are out of range are
	 * ignored.
	 *
	 * @param start the start index of the items to deselect
	 * @param end   the end index of the items to deselect
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 */
	public void deselect(int start, int end) {
		checkWidget();
		if (start < 0 || end < 0 || start > end || start >= lines.size())
			return;
		for (int i = start; i <= end; i++) {
			deselect(i);
		}
	}

	/**
	 * Deselects all selected items in the receiver.
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 */
	public void deselectAll() {
		checkWidget();
		this.selectedLines.clear();
	}

	/**
	 * Returns the zero-relative index of the item which currently has the focus in
	 * the receiver, or -1 if no item has focus.
	 *
	 * @return the index of the selected item
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 */
	public int getFocusIndex() {
		checkWidget();
		int result = (int) OS.SendMessage(handle, OS.LB_GETCARETINDEX, 0, 0);
		if (result == 0) {
			int count = (int) OS.SendMessage(handle, OS.LB_GETCOUNT, 0, 0);
			if (count == 0)
				return -1;
		}
		return result;
	}

	/**
	 * Returns the item at the given, zero-relative index in the receiver. Throws an
	 * exception if the index is out of range.
	 *
	 * @param index the index of the item to return
	 * @return the item at the given index
	 *
	 * @exception IllegalArgumentException
	 *                                     <ul>
	 *                                     <li>ERROR_INVALID_RANGE - if the index is
	 *                                     not between 0 and the number of elements
	 *                                     in the list minus 1 (inclusive)</li>
	 *                                     </ul>
	 * @exception SWTException
	 *                                     <ul>
	 *                                     <li>ERROR_WIDGET_DISPOSED - if the
	 *                                     receiver has been disposed</li>
	 *                                     <li>ERROR_THREAD_INVALID_ACCESS - if not
	 *                                     called from the thread that created the
	 *                                     receiver</li>
	 *                                     </ul>
	 */
	public String getItem(int index) {
		checkWidget();
		return this.lines.get(index);
	}

	/**
	 * Returns the number of items contained in the receiver.
	 *
	 * @return the number of items
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 */
	public int getItemCount() {
		checkWidget();
		return this.lines.size();
	}

	/**
	 * Returns the height of the area which would be used to display <em>one</em> of
	 * the items in the list.
	 *
	 * @return the height of one item
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 */
	public int getItemHeight() {
		checkWidget();
		return DPIUtil.scaleDown(getItemHeightInPixels(), getZoom());
	}

	int getItemHeightInPixels() {
		int result = (int) OS.SendMessage(handle, OS.LB_GETITEMHEIGHT, 0, 0);
		if (result == OS.LB_ERR)
			error(SWT.ERROR_CANNOT_GET_ITEM_HEIGHT);
		return result;
	}

	/**
	 * Returns a (possibly empty) array of <code>String</code>s which are the items
	 * in the receiver.
	 * <p>
	 * Note: This is not the actual structure used by the receiver to maintain its
	 * list of items, so modifying the array will not affect the receiver.
	 * </p>
	 *
	 * @return the items in the receiver's list
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 */
	public String[] getItems() {
		checkWidget();
		int count = getItemCount();
		String[] result = new String[count];
		for (int i = 0; i < count; i++)
			result[i] = getItem(i);
		return result;
	}

	/**
	 * Returns an array of <code>String</code>s that are currently selected in the
	 * receiver. The order of the items is unspecified. An empty array indicates
	 * that no items are selected.
	 * <p>
	 * Note: This is not the actual structure used by the receiver to maintain its
	 * selection, so modifying the array will not affect the receiver.
	 * </p>
	 *
	 * @return an array representing the selection
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 */
	public String[] getSelection() {
		checkWidget();
		int[] indices = getSelectionIndices();
		String[] result = new String[indices.length];
		for (int i = 0; i < indices.length; i++) {
			result[i] = getItem(indices[i]);
		}
		return result;
	}

	/**
	 * Returns the number of selected items contained in the receiver.
	 *
	 * @return the number of selected items
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 */
	public int getSelectionCount() {
		checkWidget();
		return this.selectedLines.size();
	}

	/**
	 * Returns the zero-relative index of the item which is currently selected in
	 * the receiver, or -1 if no item is selected.
	 *
	 * @return the index of the selected item or -1
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 */
	public int getSelectionIndex() {
		checkWidget();
		return (getSelectionIndices().length > 0) ? getSelectionIndices()[0] : -1;
	}

	/**
	 * Returns the zero-relative indices of the items which are currently selected
	 * in the receiver. The order of the indices is unspecified. The array is empty
	 * if no items are selected.
	 * <p>
	 * Note: This is not the actual structure used by the receiver to maintain its
	 * selection, so modifying the array will not affect the receiver.
	 * </p>
	 *
	 * @return the array of indices of the selected items
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 */
	public int[] getSelectionIndices() {
		checkWidget();
		return this.selectedLines.stream().mapToInt(Integer::intValue).toArray();
	}

	/**
	 * Returns the zero-relative index of the item which is currently at the top of
	 * the receiver. This index can change when items are scrolled or new items are
	 * added or removed.
	 *
	 * @return the index of the top item
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 */
	public int getTopIndex() {
		checkWidget();
		return (int) this.topIndex;
	}

	/**
	 * Gets the index of an item.
	 * <p>
	 * The list is searched starting at 0 until an item is found that is equal to
	 * the search item. If no item is found, -1 is returned. Indexing is zero based.
	 *
	 * @param string the search item
	 * @return the index of the item
	 *
	 * @exception IllegalArgumentException
	 *                                     <ul>
	 *                                     <li>ERROR_NULL_ARGUMENT - if the string
	 *                                     is null</li>
	 *                                     </ul>
	 * @exception SWTException
	 *                                     <ul>
	 *                                     <li>ERROR_WIDGET_DISPOSED - if the
	 *                                     receiver has been disposed</li>
	 *                                     <li>ERROR_THREAD_INVALID_ACCESS - if not
	 *                                     called from the thread that created the
	 *                                     receiver</li>
	 *                                     </ul>
	 */
	public int indexOf(String string) {
		return indexOf(string, 0);
	}

	/**
	 * Searches the receiver's list starting at the given, zero-relative index until
	 * an item is found that is equal to the argument, and returns the index of that
	 * item. If no item is found or the starting index is out of range, returns -1.
	 *
	 * @param string the search item
	 * @param start  the zero-relative index at which to start the search
	 * @return the index of the item
	 *
	 * @exception IllegalArgumentException
	 *                                     <ul>
	 *                                     <li>ERROR_NULL_ARGUMENT - if the string
	 *                                     is null</li>
	 *                                     </ul>
	 * @exception SWTException
	 *                                     <ul>
	 *                                     <li>ERROR_WIDGET_DISPOSED - if the
	 *                                     receiver has been disposed</li>
	 *                                     <li>ERROR_THREAD_INVALID_ACCESS - if not
	 *                                     called from the thread that created the
	 *                                     receiver</li>
	 *                                     </ul>
	 */
	public int indexOf(String string, int start) {
		checkWidget();
		if (string == null)
			error(SWT.ERROR_NULL_ARGUMENT);

		return this.lines.indexOf(string);

	}

	/**
	 * Returns <code>true</code> if the item is selected, and <code>false</code>
	 * otherwise. Indices out of range are ignored.
	 *
	 * @param index the index of the item
	 * @return the selection state of the item at the index
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 */
	public boolean isSelected(int index) {
		checkWidget();
		return selectedLines.contains(index);
	}

	@Override
	boolean isUseWsBorder() {
		return super.isUseWsBorder() || ((display != null) && display.useWsBorderList);
	}

	/**
	 * Removes the items from the receiver at the given zero-relative indices.
	 *
	 * @param indices the array of indices of the items
	 *
	 * @exception IllegalArgumentException
	 *                                     <ul>
	 *                                     <li>ERROR_INVALID_RANGE - if the index is
	 *                                     not between 0 and the number of elements
	 *                                     in the list minus 1 (inclusive)</li>
	 *                                     <li>ERROR_NULL_ARGUMENT - if the indices
	 *                                     array is null</li>
	 *                                     </ul>
	 * @exception SWTException
	 *                                     <ul>
	 *                                     <li>ERROR_WIDGET_DISPOSED - if the
	 *                                     receiver has been disposed</li>
	 *                                     <li>ERROR_THREAD_INVALID_ACCESS - if not
	 *                                     called from the thread that created the
	 *                                     receiver</li>
	 *                                     </ul>
	 */
	public void remove(int[] indices) {
		checkWidget();
		if (indices == null)
			error(SWT.ERROR_NULL_ARGUMENT);
		if (indices.length == 0)
			return;
		this.lines.removeAll(Arrays.asList(indices));
		redraw();
	}

	/**
	 * Removes the item from the receiver at the given zero-relative index.
	 *
	 * @param index the index for the item
	 *
	 * @exception IllegalArgumentException
	 *                                     <ul>
	 *                                     <li>ERROR_INVALID_RANGE - if the index is
	 *                                     not between 0 and the number of elements
	 *                                     in the list minus 1 (inclusive)</li>
	 *                                     </ul>
	 * @exception SWTException
	 *                                     <ul>
	 *                                     <li>ERROR_WIDGET_DISPOSED - if the
	 *                                     receiver has been disposed</li>
	 *                                     <li>ERROR_THREAD_INVALID_ACCESS - if not
	 *                                     called from the thread that created the
	 *                                     receiver</li>
	 *                                     </ul>
	 */
	public void remove(int index) {
		checkWidget();
		if (index < 0)
			error(SWT.ERROR_INVALID_ARGUMENT);
		this.lines.remove(index);
		redraw();
	}

	/**
	 * Removes the items from the receiver which are between the given zero-relative
	 * start and end indices (inclusive).
	 *
	 * @param start the start of the range
	 * @param end   the end of the range
	 *
	 * @exception IllegalArgumentException
	 *                                     <ul>
	 *                                     <li>ERROR_INVALID_RANGE - if either the
	 *                                     start or end are not between 0 and the
	 *                                     number of elements in the list minus 1
	 *                                     (inclusive)</li>
	 *                                     </ul>
	 * @exception SWTException
	 *                                     <ul>
	 *                                     <li>ERROR_WIDGET_DISPOSED - if the
	 *                                     receiver has been disposed</li>
	 *                                     <li>ERROR_THREAD_INVALID_ACCESS - if not
	 *                                     called from the thread that created the
	 *                                     receiver</li>
	 *                                     </ul>
	 */
	public void remove(int start, int end) {
		checkWidget();
		if (start > end)
			return;

		for (int i = start; i < end; i++) {
			remove(i);
		}
		redraw();
	}

	/**
	 * Searches the receiver's list starting at the first item until an item is
	 * found that is equal to the argument, and removes that item from the list.
	 *
	 * @param string the item to remove
	 *
	 * @exception IllegalArgumentException
	 *                                     <ul>
	 *                                     <li>ERROR_NULL_ARGUMENT - if the string
	 *                                     is null</li>
	 *                                     <li>ERROR_INVALID_ARGUMENT - if the
	 *                                     string is not found in the list</li>
	 *                                     </ul>
	 * @exception SWTException
	 *                                     <ul>
	 *                                     <li>ERROR_WIDGET_DISPOSED - if the
	 *                                     receiver has been disposed</li>
	 *                                     <li>ERROR_THREAD_INVALID_ACCESS - if not
	 *                                     called from the thread that created the
	 *                                     receiver</li>
	 *                                     </ul>
	 */
	public void remove(String string) {
		checkWidget();
		if (string == null)
			error(SWT.ERROR_NULL_ARGUMENT);
		this.lines.remove(string);
		redraw();
	}

	/**
	 * Removes all of the items from the receiver.
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 */
	public void removeAll() {
		checkWidget();
		this.lines.clear();
		redraw();
	}

	/**
	 * Removes the listener from the collection of listeners who will be notified
	 * when the user changes the receiver's selection.
	 *
	 * @param listener the listener which should no longer be notified
	 *
	 * @exception IllegalArgumentException
	 *                                     <ul>
	 *                                     <li>ERROR_NULL_ARGUMENT - if the listener
	 *                                     is null</li>
	 *                                     </ul>
	 * @exception SWTException
	 *                                     <ul>
	 *                                     <li>ERROR_WIDGET_DISPOSED - if the
	 *                                     receiver has been disposed</li>
	 *                                     <li>ERROR_THREAD_INVALID_ACCESS - if not
	 *                                     called from the thread that created the
	 *                                     receiver</li>
	 *                                     </ul>
	 *
	 * @see SelectionListener
	 * @see #addSelectionListener
	 */
	public void removeSelectionListener(SelectionListener listener) {
		checkWidget();
		if (listener == null)
			error(SWT.ERROR_NULL_ARGUMENT);
		if (eventTable == null)
			return;
		eventTable.unhook(SWT.Selection, listener);
		eventTable.unhook(SWT.DefaultSelection, listener);
	}

	/**
	 * Selects the items at the given zero-relative indices in the receiver. The
	 * current selection is not cleared before the new items are selected.
	 * <p>
	 * If the item at a given index is not selected, it is selected. If the item at
	 * a given index was already selected, it remains selected. Indices that are out
	 * of range and duplicate indices are ignored. If the receiver is single-select
	 * and multiple indices are specified, then all indices are ignored.
	 *
	 * @param indices the array of indices for the items to select
	 *
	 * @exception IllegalArgumentException
	 *                                     <ul>
	 *                                     <li>ERROR_NULL_ARGUMENT - if the array of
	 *                                     indices is null</li>
	 *                                     </ul>
	 * @exception SWTException
	 *                                     <ul>
	 *                                     <li>ERROR_WIDGET_DISPOSED - if the
	 *                                     receiver has been disposed</li>
	 *                                     <li>ERROR_THREAD_INVALID_ACCESS - if not
	 *                                     called from the thread that created the
	 *                                     receiver</li>
	 *                                     </ul>
	 *
	 * @see List#setSelection(int[])
	 */
	public void select(int[] indices) {
		checkWidget();
		if (indices == null)
			error(SWT.ERROR_NULL_ARGUMENT);
		int length = indices.length;
		if (length == 0 || ((style & SWT.SINGLE) != 0 && length > 1))
			return;
		select(indices, false);
	}

	void select(int[] indices, boolean scroll) {
		int i = 0;
		while (i < indices.length) {
			int index = indices[i];
			if (index != -1) {
				select(index, false);
			}
			i++;
		}
		if (scroll)
			showSelection();
	}

	/**
	 * Selects the item at the given zero-relative index in the receiver's list. If
	 * the item at the index was already selected, it remains selected. Indices that
	 * are out of range are ignored.
	 *
	 * @param index the index of the item to select
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 */
	public void select(int index) {
		checkWidget();
		select(index, false);
	}

	void select(int index, boolean scroll) {
		if (index < 0 || index >= this.lines.size()) {
			return;
		}
		this.selectedLines.add(index);
		this.lastSelectedItem = index;
		redraw();
	}

	/**
	 * Selects the items in the range specified by the given zero-relative indices
	 * in the receiver. The range of indices is inclusive. The current selection is
	 * not cleared before the new items are selected.
	 * <p>
	 * If an item in the given range is not selected, it is selected. If an item in
	 * the given range was already selected, it remains selected. Indices that are
	 * out of range are ignored and no items will be selected if start is greater
	 * than end. If the receiver is single-select and there is more than one item in
	 * the given range, then all indices are ignored.
	 *
	 * @param start the start of the range
	 * @param end   the end of the range
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 *
	 * @see List#setSelection(int,int)
	 */
	public void select(int start, int end) {
		checkWidget();
		if (end < 0 || start > end || ((style & SWT.SINGLE) != 0 && start != end))
			return;
		int count = this.lines.size();
		if (count == 0 || start >= count)
			return;
		start = Math.max(0, start);
		end = Math.min(end, count - 1);
		if ((style & SWT.SINGLE) != 0) {
			select(start, false);
		} else {
			select(start, end, false);
		}
	}

	void select(int start, int end, boolean scroll) {
		if (start == end) {
			select(start, scroll);
			return;
		}
		for (int i = start; i <= end; i++) {
			select(i, scroll);
		}

		if (scroll)
			showSelection();
	}

	/**
	 * Selects all of the items in the receiver.
	 * <p>
	 * If the receiver is single-select, do nothing.
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 */
	public void selectAll() {
		this.selectedLines.clear();
		for (int i = 0; i < this.lines.size(); i++) {
			this.selectedLines.add(i);
			this.lastSelectedItem = i;
		}
	}

	void setFocusIndex(int index) {
		// checkWidget ();
		int count = this.lines.size();
		if (!(0 <= index && index < count))
			return;
//		OS.SendMessage(handle, OS.LB_SETCARETINDEX, index, 0);
	}

	@Override
	public void setFont(Font font) {
		checkWidget();
		super.setFont(font);
		if ((style & SWT.H_SCROLL) != 0)
			setScrollWidth();
	}

	/**
	 * Sets the text of the item in the receiver's list at the given zero-relative
	 * index to the string argument.
	 *
	 * @param index  the index for the item
	 * @param string the new text for the item
	 *
	 * @exception IllegalArgumentException
	 *                                     <ul>
	 *                                     <li>ERROR_INVALID_RANGE - if the index is
	 *                                     not between 0 and the number of elements
	 *                                     in the list minus 1 (inclusive)</li>
	 *                                     <li>ERROR_NULL_ARGUMENT - if the string
	 *                                     is null</li>
	 *                                     </ul>
	 * @exception SWTException
	 *                                     <ul>
	 *                                     <li>ERROR_WIDGET_DISPOSED - if the
	 *                                     receiver has been disposed</li>
	 *                                     <li>ERROR_THREAD_INVALID_ACCESS - if not
	 *                                     called from the thread that created the
	 *                                     receiver</li>
	 *                                     </ul>
	 */
	public void setItem(int index, String string) {
		checkWidget();
		if (string == null)
			error(SWT.ERROR_NULL_ARGUMENT);
		this.lines.set(index, string);
		redraw();
	}

	/**
	 * Sets the receiver's items to be the given array of items.
	 *
	 * @param items the array of items
	 *
	 * @exception IllegalArgumentException
	 *                                     <ul>
	 *                                     <li>ERROR_NULL_ARGUMENT - if the items
	 *                                     array is null</li>
	 *                                     <li>ERROR_INVALID_ARGUMENT - if an item
	 *                                     in the items array is null</li>
	 *                                     </ul>
	 * @exception SWTException
	 *                                     <ul>
	 *                                     <li>ERROR_WIDGET_DISPOSED - if the
	 *                                     receiver has been disposed</li>
	 *                                     <li>ERROR_THREAD_INVALID_ACCESS - if not
	 *                                     called from the thread that created the
	 *                                     receiver</li>
	 *                                     </ul>
	 */
	public void setItems(String... items) {
		checkWidget();
		if (items == null)
			error(SWT.ERROR_NULL_ARGUMENT);
		for (String item : items) {
			if (item == null)
				error(SWT.ERROR_INVALID_ARGUMENT);
		}
		this.lines.clear();
		this.lines.addAll(Arrays.asList(items));
		redraw();
	}

	private int getTextWidth(String text) {
		GC gc = new GC(this);
		int width = gc.textExtent(text).x;
		gc.dispose();
		return width;
	}

	/**
	 * Calculates the scroll width depending on the item with the highest width
	 */
	void setScrollWidth() {
		int newWidth = 0;
		for (String line : this.lines) {
			newWidth = Math.max(newWidth, getTextWidth(line));
		}
		if (horizontalBar != null) {
			horizontalBar.setMaximum(newWidth + INSET);
		}
	}

	void setScrollWidth(char[] buffer, boolean grow) {
		GC gc = new GC(this);
		gc.setFont(getFont());
		Point textExtent = gc.textExtent(new String(buffer));
		gc.dispose();

		setScrollWidth(textExtent.x, grow);
	}

	void setScrollWidth(int newWidth, boolean grow) {
		newWidth += INSET;
		int width = getCurrentScrollWidth();
		if (grow) {
			if (newWidth <= width)
				return;
			if (horizontalBar != null) {
				horizontalBar.setMaximum(newWidth);
			}
		} else {
			if (newWidth < width)
				return;
			setScrollWidth();
		}
	}

	private int getCurrentScrollWidth() {
		if (getHorizontalBar() != null) {
			return getHorizontalBar().getMaximum();
		}
		return 0;
	}

	/**
	 * Selects the items at the given zero-relative indices in the receiver. The
	 * current selection is cleared before the new items are selected, and if
	 * necessary the receiver is scrolled to make the new selection visible.
	 * <p>
	 * Indices that are out of range and duplicate indices are ignored. If the
	 * receiver is single-select and multiple indices are specified, then all
	 * indices are ignored.
	 *
	 * @param indices the indices of the items to select
	 *
	 * @exception IllegalArgumentException
	 *                                     <ul>
	 *                                     <li>ERROR_NULL_ARGUMENT - if the array of
	 *                                     indices is null</li>
	 *                                     </ul>
	 * @exception SWTException
	 *                                     <ul>
	 *                                     <li>ERROR_WIDGET_DISPOSED - if the
	 *                                     receiver has been disposed</li>
	 *                                     <li>ERROR_THREAD_INVALID_ACCESS - if not
	 *                                     called from the thread that created the
	 *                                     receiver</li>
	 *                                     </ul>
	 *
	 * @see List#deselectAll()
	 * @see List#select(int[])
	 */
	public void setSelection(int[] indices) {
		checkWidget();
		if (indices == null)
			error(SWT.ERROR_NULL_ARGUMENT);
		deselectAll();
		int length = indices.length;
		if (length == 0)
			return;
		select(indices, true);
	}

	/**
	 * Sets the receiver's selection to be the given array of items. The current
	 * selection is cleared before the new items are selected, and if necessary the
	 * receiver is scrolled to make the new selection visible.
	 * <p>
	 * Items that are not in the receiver are ignored. If the receiver is
	 * single-select and multiple items are specified, then all items are ignored.
	 *
	 * @param items the array of items
	 *
	 * @exception IllegalArgumentException
	 *                                     <ul>
	 *                                     <li>ERROR_NULL_ARGUMENT - if the array of
	 *                                     items is null</li>
	 *                                     </ul>
	 * @exception SWTException
	 *                                     <ul>
	 *                                     <li>ERROR_WIDGET_DISPOSED - if the
	 *                                     receiver has been disposed</li>
	 *                                     <li>ERROR_THREAD_INVALID_ACCESS - if not
	 *                                     called from the thread that created the
	 *                                     receiver</li>
	 *                                     </ul>
	 *
	 * @see List#deselectAll()
	 * @see List#select(int[])
	 * @see List#setSelection(int[])
	 */
	public void setSelection(String[] items) {
		checkWidget();
		if (items == null)
			error(SWT.ERROR_NULL_ARGUMENT);
		deselectAll();
		int length = items.length;
		if (length == 0)
			return;
		for (int i = 0; i < length; i++) {
			select(this.lines.indexOf(items[i]));
		}
	}

	/**
	 * Selects the item at the given zero-relative index in the receiver. If the
	 * item at the index was already selected, it remains selected. The current
	 * selection is first cleared, then the new item is selected, and if necessary
	 * the receiver is scrolled to make the new selection visible. Indices that are
	 * out of range are ignored.
	 *
	 * @param index the index of the item to select
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 * @see List#deselectAll()
	 * @see List#select(int)
	 */
	public void setSelection(int index) {
		checkWidget();
		deselectAll();
		select(index, true);
	}

	/**
	 * Selects the items in the range specified by the given zero-relative indices
	 * in the receiver. The range of indices is inclusive. The current selection is
	 * cleared before the new items are selected, and if necessary the receiver is
	 * scrolled to make the new selection visible.
	 * <p>
	 * Indices that are out of range are ignored and no items will be selected if
	 * start is greater than end. If the receiver is single-select and there is more
	 * than one item in the given range, then all indices are ignored.
	 *
	 * @param start the start index of the items to select
	 * @param end   the end index of the items to select
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 *
	 * @see List#deselectAll()
	 * @see List#select(int,int)
	 */
	public void setSelection(int start, int end) {
		checkWidget();
		deselectAll();
		if (end < 0 || start > end)
			return;
		int count = this.lines.size();
		if (count == 0 || start >= count)
			return;
		start = Math.max(0, start);
		end = Math.min(end, count - 1);
		select(start, end, true);
	}

	/**
	 * Sets the zero-relative index of the item which is currently at the top of the
	 * receiver. This index can change when items are scrolled or new items are
	 * added and removed.
	 *
	 * @param index the index of the top item
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 */
	public void setTopIndex(int index) {
		checkWidget();
		this.topIndex = index;
	}

	/**
	 * Shows the selection. If the selection is already showing in the receiver,
	 * this method simply returns. Otherwise, the items are scrolled until the
	 * selection is visible.
	 *
	 * @exception SWTException
	 *                         <ul>
	 *                         <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                         disposed</li>
	 *                         <li>ERROR_THREAD_INVALID_ACCESS - if not called from
	 *                         the thread that created the receiver</li>
	 *                         </ul>
	 */
	public void showSelection() {
		checkWidget();
		int index;
		if ((style & SWT.SINGLE) != 0) {
			index = (int) OS.SendMessage(handle, OS.LB_GETCURSEL, 0, 0);
		} else {
			int[] indices = new int[1];
			int result = (int) OS.SendMessage(handle, OS.LB_GETSELITEMS, 1, indices);
			index = indices[0];
			if (result != 1)
				index = -1;
		}
		if (index == -1)
			return;
		int count = (int) OS.SendMessage(handle, OS.LB_GETCOUNT, 0, 0);
		if (count == 0)
			return;
		int height = (int) OS.SendMessage(handle, OS.LB_GETITEMHEIGHT, 0, 0);
		forceResize();
		RECT rect = new RECT();
		OS.GetClientRect(handle, rect);
		int topIndex = (int) OS.SendMessage(handle, OS.LB_GETTOPINDEX, 0, 0);
		int visibleCount = Math.max(rect.bottom / height, 1);
		int bottomIndex = Math.min(topIndex + visibleCount, count) - 1;
		if (topIndex <= index && index <= bottomIndex)
			return;
		int newTop = Math.min(Math.max(index - (visibleCount / 2), 0), count - 1);
		OS.SendMessage(handle, OS.LB_SETTOPINDEX, newTop, 0);
	}

	@Override
	void updateMenuLocation(Event event) {
		Rectangle clientArea = getClientAreaInPixels();
		int x = clientArea.x, y = clientArea.y;
		int focusIndex = getFocusIndex();
		if (focusIndex != -1) {
			RECT rect = new RECT();
			long newFont, oldFont = 0;
			long hDC = OS.GetDC(handle);
			newFont = OS.SendMessage(handle, OS.WM_GETFONT, 0, 0);
			if (newFont != 0)
				oldFont = OS.SelectObject(hDC, newFont);
			int flags = OS.DT_CALCRECT | OS.DT_SINGLELINE | OS.DT_NOPREFIX;
			char[] buffer = new char[64 + 1];
			int length = (int) OS.SendMessage(handle, OS.LB_GETTEXTLEN, focusIndex, 0);
			if (length != OS.LB_ERR) {
				if (length + 1 > buffer.length) {
					buffer = new char[length + 1];
				}
				int result = (int) OS.SendMessage(handle, OS.LB_GETTEXT, focusIndex, buffer);
				if (result != OS.LB_ERR) {
					OS.DrawText(hDC, buffer, length, rect, flags);
				}
			}
			if (newFont != 0)
				OS.SelectObject(hDC, oldFont);
			OS.ReleaseDC(handle, hDC);
			x = Math.max(x, rect.right / 2);
			x = Math.min(x, clientArea.x + clientArea.width);

			OS.SendMessage(handle, OS.LB_GETITEMRECT, focusIndex, rect);
			y = Math.max(y, rect.bottom);
			y = Math.min(y, clientArea.y + clientArea.height);
		}
		Point pt = toDisplayInPixels(x, y);
		int zoom = getZoom();
		event.setLocation(DPIUtil.scaleDown(pt.x, zoom), DPIUtil.scaleDown(pt.y, zoom));
	}

	@Override
	boolean updateTextDirection(int textDirection) {
		if (textDirection == AUTO_TEXT_DIRECTION) {
			/* If auto is already in effect, there's nothing to do. */
			if ((state & HAS_AUTO_DIRECTION) != 0)
				return false;
			state |= HAS_AUTO_DIRECTION;
		} else {
			state &= ~HAS_AUTO_DIRECTION;
			if (!addedUCC /* (state & HAS_AUTO_DIRECTION) == 0 */) {
				return super.updateTextDirection(textDirection);
			}
		}
		int count = (int) OS.SendMessage(handle, OS.LB_GETCOUNT, 0, 0);
		if (count == OS.LB_ERR)
			return false;
		int selection = (int) OS.SendMessage(handle, OS.LB_GETCURSEL, 0, 0);
		addedUCC = false;
		while (count-- > 0) {
			int length = (int) OS.SendMessage(handle, OS.LB_GETTEXTLEN, count, 0);
			if (length == OS.LB_ERR)
				break;
			if (length == 0)
				continue;
			char[] buffer = new char[length + 1];
			if (OS.SendMessage(handle, OS.LB_GETTEXT, count, buffer) == OS.LB_ERR)
				break;
			if (OS.SendMessage(handle, OS.LB_DELETESTRING, count, 0) == OS.LB_ERR)
				break;
			if ((state & HAS_AUTO_DIRECTION) == 0) {
				/* Should remove UCC */
				System.arraycopy(buffer, 1, buffer, 0, length);
			}
			/* Adding UCC is handled in OS.LB_INSERTSTRING */
			if (OS.SendMessage(handle, OS.LB_INSERTSTRING, count, buffer) == OS.LB_ERR)
				break;
		}
		if (selection != OS.LB_ERR) {
			OS.SendMessage(handle, OS.LB_SETCURSEL, selection, 0);
		}
		return textDirection == AUTO_TEXT_DIRECTION || super.updateTextDirection(textDirection);
	}

	@Override
	int widgetStyle() {
		int bits = super.widgetStyle() | OS.LBS_NOTIFY | OS.LBS_NOINTEGRALHEIGHT;
		if ((style & SWT.SINGLE) != 0)
			return bits;
		if ((style & SWT.MULTI) != 0) {
			if ((style & SWT.SIMPLE) != 0)
				return bits | OS.LBS_MULTIPLESEL;
			return bits | OS.LBS_EXTENDEDSEL;
		}
		return bits;
	}

	@Override
	TCHAR windowClass() {
		return ListClass;
	}

	@Override
	long windowProc() {
		return ListProc;
	}

	private static void handleDPIChange(Widget widget, int newZoom, float scalingFactor) {
		if (!(widget instanceof List list)) {
			return;
		}
		if ((list.style & SWT.H_SCROLL) != 0) {
			// Recalculate the Scroll width, as length of items has changed
			list.setScrollWidth();
		}
	}
}
