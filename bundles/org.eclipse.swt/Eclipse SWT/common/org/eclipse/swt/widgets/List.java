package org.eclipse.swt.widgets;

import java.util.*;

import org.eclipse.swt.*;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.*;
import org.eclipse.swt.internal.win32.*;

public class List extends Scrollable implements ICustomWidget {
	static final int INSET = 3;

	private java.util.List<String> items = new ArrayList<>();
	private java.util.List<Integer> selectedItems = new ArrayList<>();

	private int topIndex = 0;
	private Integer lastSelectedItem = 0;

	public List(Composite parent, int style) {
		super(parent, checkStyle(style));
		addListeners();
//		showScrollBar();
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
		addDisposeListener(e -> dispose());
		addPaintListener(this::paintControl);

		addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				onKeyReleased(e);
			}
		});

		ScrollBar horizontalBar = getHorizontalBar();
		if (horizontalBar != null) {
			horizontalBar.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					List.this.scrollBarSelectionChanged(e);
				}
			});
		}
		ScrollBar verticalBar = getVerticalBar();
		if (verticalBar != null) {
			verticalBar.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					List.this.topIndex = verticalBar.getSelection();
					List.this.scrollBarSelectionChanged(e);
				}
			});
		}

		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseDown(MouseEvent e) {
				List.this.onMouseDown(e);
			}

			@Override
			public void mouseUp(MouseEvent e) {
				List.this.onMouseUp(e);
			}
		});

		addListener(SWT.Resize, event -> {
			if (event.type == SWT.Resize) {
				onResize();
			}
		});
	}

	private void onResize() {
		redraw();
	}

	private void paintControl(PaintEvent e) {
		if (!isVisible()) {
			return;
		}
		GC gc = e.gc != null ? e.gc : new GC(this);
		doPaint(e);
		gc.dispose();
	}

	private void doPaint(PaintEvent e) {
		Rectangle r = getBounds();
		if (r.width == 0 && r.height == 0) {
			return;
		}
		Rectangle visibleArea = getVisibleArea();
		drawText(e, visibleArea);
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

	private void drawText(PaintEvent e, Rectangle visibleArea) {
		for (int i = 0; i < this.items.size(); i++) {
			drawTextLine(items.get(i), i, e.x, e.y, visibleArea, e.gc);
		}
	}

	private void drawTextLine(String text, int lineNumber, int x, int y, Rectangle visibleArea, GC gc) {
		Point textExtent = gc.textExtent(text);
		Rectangle clientArea = getClientArea();

		int _x = calculateHorizontalAlignment(x, textExtent, clientArea);
		int _y = y + lineNumber * textExtent.y - visibleArea.y;

		_x -= visibleArea.x;

		if ((style & SWT.BORDER) != 0) {
			int borderWidth = getBorderWidth();
			_x += borderWidth;
			_y += borderWidth;
		}

		// handle Vertical Scroll
		_y -= this.topIndex * textExtent.y;
		// handle Horizontal Scroll
		if (horizontalBar != null) {
			_x -= horizontalBar.getSelection();
		}

		if (this.selectedItems.size() != 0 && this.selectedItems.contains(lineNumber)) {
			drawSelectedText(text, gc, _x, _y);
		} else {
			gc.drawText(text, _x, _y, true);
		}
	}

	private void drawSelectedText(String text, GC gc, int _x, int _y) {
		Color background = gc.getBackground();
		Color foreground = gc.getForeground();
		gc.setForeground(getDisplay().getSystemColor(SWT.COLOR_LIST_SELECTION_TEXT));
		gc.setBackground(getDisplay().getSystemColor(SWT.COLOR_LIST_SELECTION));
		gc.drawText(text, _x, _y);
		gc.setForeground(foreground);
		gc.setBackground(background);
	}

	private int calculateHorizontalAlignment(int x, Point textExtent, Rectangle clientArea) {
		if ((style & SWT.CENTER) != 0) {
			return (clientArea.width - textExtent.x) / 2;
		} else if ((style & SWT.RIGHT) != 0) {
			return clientArea.width - textExtent.x;
		}
		return x;
	}

	private void onKeyPressed(KeyEvent event) {
	}

	private void onKeyReleased(KeyEvent event) {
		boolean isShiftPressed = (event.stateMask & SWT.SHIFT) != 0;
		switch (event.keyCode) {
		case SWT.ARROW_DOWN -> handleArrowKeys(1, isShiftPressed);
		case SWT.ARROW_UP -> handleArrowKeys(-1, isShiftPressed);
		default -> {
		}
		}
		redraw();
	}

	private void handleArrowKeys(int offset, boolean isShiftPressed) {
		if (isShiftPressed) {
			selectMultipleLine(offset);
		} else {
			moveSelectedLine(offset);
		}
	}

	private void selectMultipleLine(int offset) {
		int newIndex = calculateNewIndex(this.lastSelectedItem, offset);
		if (this.selectedItems.contains(newIndex)) {
			this.selectedItems.remove(Integer.valueOf(newIndex - offset));
		} else {
			this.selectedItems.add(newIndex);
		}
		this.lastSelectedItem = newIndex;
	}

	private void moveSelectedLine(int offset) {
		if (this.selectedItems.size() == 1) {
			int currentIndex = selectedItems.iterator().next();
			selectedItems.clear();
			selectedItems.add(calculateNewIndex(currentIndex, offset));
		}
	}

	private int calculateNewIndex(int currentIndex, int offset) {
		int newIndex = currentIndex + offset;
		return Math.max(0, Math.min(newIndex, items.size() - 1));
	}

	private void scrollBarSelectionChanged(SelectionEvent e) {
		redraw();
	}

	private void onMouseDown(MouseEvent e) {
		redraw();
	}

	private void onMouseUp(MouseEvent e) {
		if ((e.stateMask & SWT.BUTTON1) != 0) {
			if ((e.stateMask & SWT.CTRL) != 0) {
				handleCtrlClick(e.x, e.y);
			} else {
				toggleSelectedLine(e);
			}
		}
		redraw();
	}

	private void handleCtrlClick(int x, int y) {
		int clickedLine = getTextLocation(x, y);
		if (clickedLine >= 0 && clickedLine < this.items.size()) {
			if (this.selectedItems.contains(clickedLine)) {
				this.selectedItems.remove(Integer.valueOf(clickedLine));
			} else {
				this.selectedItems.add(clickedLine);
				this.lastSelectedItem = clickedLine;
			}
		}
	}

	private void toggleSelectedLine(MouseEvent e) {
		Integer selectedLine = Integer.valueOf(getTextLocation(e.x, e.y));
		this.selectedItems.clear();
		this.selectedItems.add(selectedLine);
		this.lastSelectedItem = selectedLine;
	}

	private int getTextLocation(int selectedX, int selectedY) {
		Rectangle visibleArea = getVisibleArea();
		int y = Math.max(selectedY + visibleArea.y, 0);

		GC gc = new GC(this);
		String[] textLines = this.items.toArray(new String[0]);
		int clickedLine = Math.min(Math.round(y / getLineHeight(gc)), textLines.length - 1);
		int selectedLine = Math.min(clickedLine, textLines.length - 1);
		return selectedLine;
	}

	private int getLineHeight(GC gc) {
		checkWidget();
		if (this.items.isEmpty()) {
			return 0;
		}
		return gc.textExtent(this.items.get(0)).y;
	}

	private void adjustCanvasSize(GC gc) {
		Point maxTextExtent = new Point(0, 0);

		for (String line : items) {
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

	public void add(String string) {
		checkWidget();
		if (string == null)
			error(SWT.ERROR_NULL_ARGUMENT);

		this.items.add(string);
		updateScrollBarWithTextSize();
		redraw();

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
			String str = this.items.isEmpty() ? "" : this.items.get(0);
			Point size = gc.textExtent(str);
			if (str.length() > 0) {
				width = (int) Math.ceil(size.x);
			}
			height = (int) Math.ceil(size.y);
		} else {
			Point size = null;
			for (String line : this.items) {
				size = gc.textExtent(line);
				width = Math.max(width, size.x);
			}
			height = size != null ? size.y * this.items.size() : 0;
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

	private void updateScrollBarWithTextSize() {
		Rectangle clientArea = getClientArea();
		Point maxTextSize = computeTextSize();

		if (verticalBar != null) {
			int thumb = clientArea.height / getLineHeight();
			verticalBar.setMaximum(this.items.size());
			verticalBar.setMinimum(0);
			verticalBar.setThumb(thumb);
			verticalBar.setVisible(maxTextSize.y > clientArea.height);
		}

		if (horizontalBar != null) {
			horizontalBar.setMaximum(maxTextSize.x);
			horizontalBar.setMinimum(0);
			horizontalBar.setThumb(clientArea.width / maxTextSize.x);
			horizontalBar.setVisible(maxTextSize.x > clientArea.width);
		}
	}

	public void add(String string, int index) {
		checkWidget();
		if (string == null)
			error(SWT.ERROR_NULL_ARGUMENT);
		if (index == -1)
			error(SWT.ERROR_INVALID_RANGE);
		this.items.add(index, string);
		redraw();
	}

	public void addSelectionListener(SelectionListener listener) {
		addTypedListener(listener, SWT.Selection, SWT.DefaultSelection);
	}

	static int checkStyle(int style) {
		return checkBits(style, SWT.SINGLE, SWT.MULTI, 0, 0, 0, 0);
	}

	public void deselect(int[] indices) {
		checkWidget();
		if (indices == null)
			error(SWT.ERROR_NULL_ARGUMENT);
		for (int index : indices) {
			deselect(index);
		}
	}

	public void deselect(int index) {
		checkWidget();
		if (index != -1) {
			this.selectedItems.remove(Integer.valueOf(index));
		}
	}

	public void deselect(int start, int end) {
		checkWidget();
		if (start >= 0 && end >= 0 && start <= end && start < items.size()) {
			for (int i = start; i <= end; i++) {
				deselect(i);
			}
		}
	}

	public void deselectAll() {
		checkWidget();
		this.selectedItems.clear();
	}

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

	public String getItem(int index) {
		checkWidget();
		return this.items.get(index);
	}

	public int getItemCount() {
		checkWidget();
		return this.items.size();
	}

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

	public String[] getItems() {
		checkWidget();
		int count = getItemCount();
		String[] result = new String[count];
		for (int i = 0; i < count; i++)
			result[i] = getItem(i);
		return result;
	}

	public String[] getSelection() {
		checkWidget();
		int[] indices = getSelectionIndices();
		String[] result = new String[indices.length];
		for (int i = 0; i < indices.length; i++) {
			result[i] = getItem(indices[i]);
		}
		return result;
	}

	public int getSelectionCount() {
		checkWidget();
		return this.selectedItems.size();
	}

	public int getSelectionIndex() {
		checkWidget();
		return (getSelectionIndices().length > 0) ? getSelectionIndices()[0] : -1;
	}

	public int[] getSelectionIndices() {
		checkWidget();
		return this.selectedItems.stream().mapToInt(Integer::intValue).toArray();
	}

	public int getTopIndex() {
		checkWidget();
		return (int) this.topIndex;
	}

	public int indexOf(String string) {
		return indexOf(string, 0);
	}

	public int indexOf(String string, int start) {
		checkWidget();
		if (string == null)
			error(SWT.ERROR_NULL_ARGUMENT);

		return this.items.indexOf(string);

	}

	public boolean isSelected(int index) {
		checkWidget();
		return selectedItems.contains(index);
	}

	@Override
	boolean isUseWsBorder() {
		return super.isUseWsBorder() || ((display != null) && display.useWsBorderList);
	}

	public void remove(int[] indices) {
		checkWidget();
		if (indices == null)
			error(SWT.ERROR_NULL_ARGUMENT);
		if (indices.length == 0)
			return;
		this.items.removeAll(Arrays.asList(indices));
		redraw();
	}

	public void remove(int index) {
		checkWidget();
		if (index < 0)
			error(SWT.ERROR_INVALID_ARGUMENT);
		this.items.remove(index);
		redraw();
	}

	public void remove(int start, int end) {
		checkWidget();
		if (start > end)
			return;

		for (int i = start; i < end; i++) {
			remove(i);
		}
		redraw();
	}

	public void remove(String string) {
		checkWidget();
		if (string == null)
			error(SWT.ERROR_NULL_ARGUMENT);
		this.items.remove(string);
		redraw();
	}

	public void removeAll() {
		checkWidget();
		this.items.clear();
		redraw();
	}

	public void removeSelectionListener(SelectionListener listener) {
		checkWidget();
		if (listener == null)
			error(SWT.ERROR_NULL_ARGUMENT);
		if (eventTable == null)
			return;
		eventTable.unhook(SWT.Selection, listener);
		eventTable.unhook(SWT.DefaultSelection, listener);
	}

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

	public void select(int index) {
		checkWidget();
		select(index, false);
	}

	void select(int index, boolean scroll) {
		if (index < 0 || index >= this.items.size()) {
			return;
		}
		this.selectedItems.add(index);
		this.lastSelectedItem = index;
		redraw();
	}

	public void select(int start, int end) {
		checkWidget();
		if (end < 0 || start > end || ((style & SWT.SINGLE) != 0 && start != end))
			return;
		int count = this.items.size();
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

	public void selectAll() {
		this.selectedItems.clear();
		for (int i = 0; i < this.items.size(); i++) {
			this.selectedItems.add(i);
			this.lastSelectedItem = i;
		}
	}

	void setFocusIndex(int index) {
		// checkWidget ();
		int count = this.items.size();
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

	public void setItem(int index, String string) {
		checkWidget();
		if (string == null)
			error(SWT.ERROR_NULL_ARGUMENT);
		this.items.set(index, string);
		redraw();
	}

	public void setItems(String... items) {
		checkWidget();
		if (items == null)
			error(SWT.ERROR_NULL_ARGUMENT);
		for (String item : items) {
			if (item == null)
				error(SWT.ERROR_INVALID_ARGUMENT);
		}
		this.items.clear();
		this.items.addAll(Arrays.asList(items));
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
		for (String line : this.items) {
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

	public void setSelection(String[] items) {
		checkWidget();
		if (items == null)
			error(SWT.ERROR_NULL_ARGUMENT);
		deselectAll();
		int length = items.length;
		if (length == 0)
			return;
		for (int i = 0; i < length; i++) {
			select(this.items.indexOf(items[i]));
		}
	}

	public void setSelection(int index) {
		checkWidget();
		deselectAll();
		select(index, true);
	}

	public void setSelection(int start, int end) {
		checkWidget();
		deselectAll();
		if (end < 0 || start > end)
			return;
		int count = this.items.size();
		if (count == 0 || start >= count)
			return;
		start = Math.max(0, start);
		end = Math.min(end, count - 1);
		select(start, end, true);
	}

	public void setTopIndex(int index) {
		checkWidget();
		this.topIndex = index;
	}

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

//	@Override
//	boolean updateTextDirection(int textDirection) {
//		if (textDirection == AUTO_TEXT_DIRECTION) {
//			/* If auto is already in effect, there's nothing to do. */
//			if ((state & HAS_AUTO_DIRECTION) != 0)
//				return false;
//			state |= HAS_AUTO_DIRECTION;
//		} else {
//			state &= ~HAS_AUTO_DIRECTION;
//			if (!addedUCC /* (state & HAS_AUTO_DIRECTION) == 0 */) {
//				return super.updateTextDirection(textDirection);
//			}
//		}
//		int count = (int) OS.SendMessage(handle, OS.LB_GETCOUNT, 0, 0);
//		if (count == OS.LB_ERR)
//			return false;
//		int selection = (int) OS.SendMessage(handle, OS.LB_GETCURSEL, 0, 0);
//		addedUCC = false;
//		while (count-- > 0) {
//			int length = (int) OS.SendMessage(handle, OS.LB_GETTEXTLEN, count, 0);
//			if (length == OS.LB_ERR)
//				break;
//			if (length == 0)
//				continue;
//			char[] buffer = new char[length + 1];
//			if (OS.SendMessage(handle, OS.LB_GETTEXT, count, buffer) == OS.LB_ERR)
//				break;
//			if (OS.SendMessage(handle, OS.LB_DELETESTRING, count, 0) == OS.LB_ERR)
//				break;
//			if ((state & HAS_AUTO_DIRECTION) == 0) {
//				/* Should remove UCC */
//				System.arraycopy(buffer, 1, buffer, 0, length);
//			}
//			/* Adding UCC is handled in OS.LB_INSERTSTRING */
//			if (OS.SendMessage(handle, OS.LB_INSERTSTRING, count, buffer) == OS.LB_ERR)
//				break;
//		}
//		if (selection != OS.LB_ERR) {
//			OS.SendMessage(handle, OS.LB_SETCURSEL, selection, 0);
//		}
//		return textDirection == AUTO_TEXT_DIRECTION || super.updateTextDirection(textDirection);
//	}

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
}
