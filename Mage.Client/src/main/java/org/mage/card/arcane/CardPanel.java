package org.mage.card.arcane;

import mage.cards.MagePermanent;
import mage.cards.TextPopup;
import mage.cards.action.ActionCallback;
import mage.cards.action.TransferData;
import mage.client.plugins.adapters.MageActionCallback;
import mage.client.plugins.impl.Plugins;
import mage.client.util.audio.AudioManager;
import mage.constants.CardType;
import mage.constants.EnlargeMode;
import mage.constants.SubType;
import mage.constants.SuperType;
import mage.view.AbilityView;
import mage.view.CardView;
import mage.view.PermanentView;
import mage.view.StackAbilityView;
import org.apache.log4j.Logger;
import org.mage.plugins.card.utils.impl.ImageManagerImpl;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Main class for drawing Mage card object.
 *
 * @author arcane, nantuko, noxx
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public abstract class CardPanel extends MagePermanent implements MouseListener, MouseMotionListener, MouseWheelListener, ComponentListener {

    private static final long serialVersionUID = -3272134219262184410L;

    private static final Logger LOGGER = Logger.getLogger(CardPanel.class);

    public static final double TAPPED_ANGLE = Math.PI / 2;
    public static final double FLIPPED_ANGLE = Math.PI;
    public static final float ASPECT_RATIO = 3.5f / 2.5f;
    public static final int POPUP_X_GAP = 1; // prevent tooltip window from blinking

    public static final Rectangle CARD_SIZE_FULL = new Rectangle(101, 149);

    private static final float ROT_CENTER_TO_TOP_CORNER = 1.0295630140987000315797369464196f;
    private static final float ROT_CENTER_TO_BOTTOM_CORNER = 0.7071067811865475244008443621048f;

    private CardView gameCard;
    private CardView updateCard;

    // for two faced cards
    private CardView temporary;

    private double tappedAngle = 0;
    private double flippedAngle = 0;

    private final List<MagePermanent> links = new ArrayList<>();

    public final JPanel buttonPanel;
    private JButton dayNightButton;
    private JButton showCopySourceButton;

    private boolean displayEnabled = true;
    private boolean isAnimationPanel;
    private int cardXOffset, cardYOffset, cardWidth, cardHeight;
    private int symbolWidth;

    private boolean isSelected;
    private boolean isChoosable;
    private boolean showCastingCost;
    private float alpha = 1.0f;

    private ActionCallback callback;

    protected boolean tooltipShowing;
    protected final TextPopup tooltipText;
    protected UUID gameId;
    private TransferData data = new TransferData();

    private boolean isPermanent;
    private boolean hasSickness;
    private String zone;

    // Permanent and card renders are different (another sizes and positions of panel, tapped, etc -- that's weird)
    // Some card view components support only permanents (BattlefieldPanel), but another support only cards (CardArea)
    // TODO: remove crop/size logic from CardPanel to viewers panels or make compatible for all panels
    // But testing render needs both cards and permanents. That's settings allows to disable different render logic
    private boolean needFullPermanentRender = true;

    public double transformAngle = 1;

    private boolean transformed;
    private boolean animationInProgress = false;

    private JPanel cardArea;

    // default offset, e.g. for battlefield
    private int yCardCaptionOffsetPercent = 8; // card caption offset (use for moving card caption view center, below mana icons -- for more good UI)

    // if this is set, it's opened if the user right clicks on the card panel
    private JPopupMenu popupMenu;

    public CardPanel(CardView newGameCard, UUID gameId, final boolean loadImage, ActionCallback callback, final boolean foil, Dimension dimension, boolean needFullPermanentRender) {
        // Store away params
        this.setGameCard(newGameCard);
        this.callback = callback;
        this.gameId = gameId;
        this.needFullPermanentRender = needFullPermanentRender;

        /*
        this.setFocusable(true);
        this.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(FocusEvent e) {
                //LOGGER.warn("focus gained " + getCard().getName());
            }

            public void focusLost(FocusEvent e) {
                //LOGGER.warn("focus lost " + getCard().getName());
            }
        });
         */

        // Gather info about the card (all card maniputations possible with permanents only, also render can be different)
        this.isPermanent = this.getGameCard() instanceof PermanentView && !this.getGameCard().inViewerOnly();
        if (isPermanent) {
            this.hasSickness = ((PermanentView) this.getGameCard()).hasSummoningSickness();
        }

        // Set to requested size
        this.setCardBounds(0, 0, dimension.width, dimension.height);

        // Create button panel for Transform and Show Source (copied cards)
        buttonPanel = new JPanel();
        buttonPanel.setLayout(null);
        buttonPanel.setOpaque(false);
        buttonPanel.setVisible(true);
        add(buttonPanel);

        // Both card rendering implementations have a transform button
        if (this.getGameCard().canTransform()) {
            // Create the day night button
            dayNightButton = new JButton("");
            dayNightButton.setSize(32, 32);
            dayNightButton.setToolTipText("This permanent is a double faced card. To see the back face card, push this button or turn mouse wheel down while hovering with the mouse pointer over the permanent.");
            BufferedImage day = ImageManagerImpl.instance.getDayImage();
            dayNightButton.setIcon(new ImageIcon(day));
            dayNightButton.addActionListener(e -> {
                // if card is being rotated, ignore action performed
                // if card is tapped, no visual transforming is possible (implementation limitation)
                // if card is permanent, it will be rotated by Mage, so manual rotate should be possible
                if (animationInProgress || isTapped() || isPermanent) {
                    return;
                }
                Animation.transformCard(CardPanel.this, CardPanel.this, true);
            });

            // Add it
            buttonPanel.add(dayNightButton);
        }

        // Both card rendering implementations have a view copy source button
        if (this.getGameCard() instanceof PermanentView) {
            // Create the show source button
            showCopySourceButton = new JButton("");
            showCopySourceButton.setSize(32, 32);
            showCopySourceButton.setToolTipText("This permanent is copying a target. To see original card, push this button or turn mouse wheel down while hovering with the mouse pointer over the permanent.");
            showCopySourceButton.setVisible(((PermanentView) this.getGameCard()).isCopy());
            showCopySourceButton.setIcon(new ImageIcon(ImageManagerImpl.instance.getCopyInformIconImage()));
            showCopySourceButton.addActionListener(e -> {
                ActionCallback callback1 = Plugins.instance.getActionCallback();
                ((MageActionCallback) callback1).enlargeCard(EnlargeMode.COPY);
            });

            // Add it
            buttonPanel.add(showCopySourceButton);
        }

        // JPanel setup
        setBackground(Color.black);
        setOpaque(false);

        // JPanel event listeners
        addMouseListener(this);
        addMouseMotionListener(this);
        addMouseWheelListener(this);
        addComponentListener(this);

        // Tooltip for card details hover
        String cardType = getType(newGameCard);
        tooltipText = new TextPopup();
        tooltipText.setText(getText(cardType, newGameCard));

        // Animation setup
        setTappedAngle(isTapped() ? CardPanel.TAPPED_ANGLE : 0);
        setFlippedAngle(isFlipped() ? CardPanel.FLIPPED_ANGLE : 0);
    }

    @Override
    public void doLayout() {
        // Position transform and show source buttons
        buttonPanel.setLocation(cardXOffset, cardYOffset);
        buttonPanel.setSize(cardWidth, cardHeight);
        int x = cardWidth / 20;
        int y = cardHeight / 10;
        if (dayNightButton != null) {
            dayNightButton.setLocation(x, y);
            y += 25;
            y += 5;
        }
        if (showCopySourceButton != null) {
            showCopySourceButton.setLocation(x, y);
        }
    }

    public final void initialDraw() {
        // Kick off
        if (getGameCard().isTransformed()) {
            // this calls updateImage
            toggleTransformed();
        } else {
            updateArtImage();
        }
    }

    public void setIsPermanent(boolean isPermanent) {
        this.isPermanent = isPermanent;
    }

    public void cleanUp() {
        if (dayNightButton != null) {
            for (ActionListener al : dayNightButton.getActionListeners()) {
                dayNightButton.removeActionListener(al);
            }
        }
        for (MouseListener ml : this.getMouseListeners()) {
            this.removeMouseListener(ml);
        }
        for (MouseMotionListener ml : this.getMouseMotionListeners()) {
            this.removeMouseMotionListener(ml);
        }
        for (MouseWheelListener ml : this.getMouseWheelListeners()) {
            this.removeMouseWheelListener(ml);
        }
        // this holds reference to ActionCallback forever so set it to null to prevent
        this.callback = null;
        this.data = null;
    }

    // Copy the graphical resources of another CardPanel over to this one,
    // if possible (may not be possible if they have different implementations)
    // Used when cards are moving between zones
    public abstract void transferResources(CardPanel panel);

    @Override
    public void setZone(String zone) {
        this.zone = zone;
    }

    @Override
    public String getZone() {
        return zone;
    }

    public void setDisplayEnabled(boolean displayEnabled) {
        this.displayEnabled = displayEnabled;
    }

    public boolean isDisplayEnabled() {
        return displayEnabled;
    }

    public void setAnimationPanel(boolean isAnimationPanel) {
        this.isAnimationPanel = isAnimationPanel;
    }

    public boolean isAnimationPanel() {
        return this.isAnimationPanel;
    }

    @Override
    public void setSelected(boolean isSelected) {
        this.isSelected = isSelected;
    }

    public boolean isSelected() {
        return this.isSelected;
    }

    @Override
    public List<MagePermanent> getLinks() {
        return links;
    }

    @Override
    public void setChoosable(boolean isChoosable) {
        this.isChoosable = isChoosable;
    }

    public boolean isChoosable() {
        return this.isChoosable;
    }

    public boolean hasSickness() {
        return this.hasSickness;
    }

    public boolean isPermanent() {
        return this.isPermanent;
    }

    @Override
    public void setCardAreaRef(JPanel cardArea) {
        this.cardArea = cardArea;
    }

    public void setShowCastingCost(boolean showCastingCost) {
        this.showCastingCost = showCastingCost;
    }

    public boolean getShowCastingCost() {
        return this.showCastingCost;
    }

    /**
     * Overridden by different card rendering styles
     *
     * @param g
     */
    protected abstract void paintCard(Graphics2D g);

    @Override
    public void paint(Graphics g) {
        if (!displayEnabled) {
            return;
        }
        if (!isValid()) {
            super.validate();
        }
        Graphics2D g2d = (Graphics2D) g;
        if (transformAngle < 1) {
            float edgeOffset = (cardWidth + cardXOffset) / 2f;
            g2d.translate(edgeOffset * (1 - transformAngle), 0);
            g2d.scale(transformAngle, 1);
        }
        if (getTappedAngle() + getFlippedAngle() > 0) {
            g2d = (Graphics2D) g2d.create();
            float edgeOffset = cardWidth / 2f;
            double angle = getTappedAngle() + (Math.abs(getFlippedAngle() - FLIPPED_ANGLE) < 0.001 ? 0 : getFlippedAngle());
            g2d.rotate(angle, cardXOffset + edgeOffset, cardYOffset + cardHeight - edgeOffset);
        }
        super.paint(g2d);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) (g.create());

        // Deferr to subclasses
        paintCard(g2d);

        // Done, dispose of the context
        g2d.dispose();
    }

    @Override
    public String toString() {
        return getGameCard().toString();
    }

    @Override
    public void setCardBounds(int x, int y, int cardWidth, int cardHeight) {
        if (cardWidth == this.cardWidth && cardHeight == this.cardHeight) {
            setBounds(x - cardXOffset, y - cardYOffset, getWidth(), getHeight());
            return;
        }

        this.cardWidth = cardWidth;
        this.symbolWidth = cardWidth / 7;
        this.cardHeight = cardHeight;
        if (this.isPermanent && needFullPermanentRender) {
            int rotCenterX = Math.round(cardWidth / 2f);
            int rotCenterY = cardHeight - rotCenterX;
            int rotCenterToTopCorner = Math.round(cardWidth * CardPanel.ROT_CENTER_TO_TOP_CORNER);
            int rotCenterToBottomCorner = Math.round(cardWidth * CardPanel.ROT_CENTER_TO_BOTTOM_CORNER);
            int xOffset = getXOffset(cardWidth);
            int yOffset = getYOffset(cardWidth, cardHeight);
            cardXOffset = -xOffset;
            cardYOffset = -yOffset;
            int width = -xOffset + rotCenterX + rotCenterToTopCorner;
            int height = -yOffset + rotCenterY + rotCenterToBottomCorner;
            setBounds(x + xOffset, y + yOffset, width, height);
        } else {
            cardXOffset = 0;
            cardYOffset = 0;
            int width = cardXOffset * 2 + cardWidth;
            int height = cardYOffset * 2 + cardHeight;
            setBounds(x - cardXOffset, y - cardYOffset, width, height);
        }
    }

    public int getXOffset(int cardWidth) {
        if (this.isPermanent && needFullPermanentRender) {
            int rotCenterX = Math.round(cardWidth / 2f);
            int rotCenterToBottomCorner = Math.round(cardWidth * CardPanel.ROT_CENTER_TO_BOTTOM_CORNER);
            int xOffset = rotCenterX - rotCenterToBottomCorner;
            return xOffset;
        } else {
            return cardXOffset;
        }
    }

    public final int getYOffset(int cardWidth, int cardHeight) {
        if (this.isPermanent && needFullPermanentRender) {
            int rotCenterX = Math.round(cardWidth / 2f);
            int rotCenterY = cardHeight - rotCenterX;
            int rotCenterToTopCorner = Math.round(cardWidth * CardPanel.ROT_CENTER_TO_TOP_CORNER);
            int yOffset = rotCenterY - rotCenterToTopCorner;
            return yOffset;
        } else {
            return cardYOffset;
        }

    }

    public final int getCardX() {
        return getX() + cardXOffset;
    }

    public final int getCardY() {
        return getY() + cardYOffset;
    }

    public final int getCardWidth() {
        return cardWidth;
    }

    public final int getCardHeight() {
        return cardHeight;
    }

    public final int getSymbolWidth() {
        return symbolWidth;
    }

    public final Point getCardLocation() {
        Point p = getLocation();
        p.x += cardXOffset;
        p.y += cardYOffset;
        return p;
    }

    public final CardView getCard() {
        return this.getGameCard();
    }

    @Override
    public void setAlpha(float alpha) {
        this.alpha = alpha;
    }

    @Override
    public final float getAlpha() {
        return alpha;
    }

    public final int getCardXOffset() {
        return cardXOffset;
    }

    public final int getCardYOffset() {
        return cardYOffset;
    }

    @Override
    public final boolean isTapped() {
        if (isPermanent) {
            return ((PermanentView) getGameCard()).isTapped();
        }
        return false;
    }

    @Override
    public final boolean isFlipped() {
        if (isPermanent) {
            return ((PermanentView) getGameCard()).isFlipped();
        }
        return false;
    }

    @Override
    public final boolean isTransformed() {
        if (isPermanent) {
            if (getGameCard().isTransformed()) {
                return !this.transformed;
            } else {
                return this.transformed;
            }
        } else {
            return this.transformed;
        }
    }

    @Override
    public void onBeginAnimation() {
        animationInProgress = true;
    }

    @Override
    public void onEndAnimation() {
        animationInProgress = false;
    }

    /**
     * Inheriting classes should implement update(CardView card) by using this.
     * However, they should ALSO call repaint() after the superclass call to
     * this function, that can't be done here as the overriders may need to do
     * things both before and after this call before repainting.
     *
     * @param card
     */
    @Override
    public void update(CardView card) {
        if (card == null) {
            return;
        }

        if (transformed && card.equals(this.temporary)) {
            // update can be called from different places (after transform click, after selection change, etc)
            // if card temporary transformed before (by icon click) then do not update full data (as example, after selection changed)
            this.isChoosable = card.isChoosable();
            this.isSelected = card.isSelected();
            return;
        } else {
            this.setUpdateCard(card);
        }

        // Animation update
        if (isPermanent && (card instanceof PermanentView)) {
            boolean needsTapping = isTapped() != ((PermanentView) card).isTapped();
            boolean needsFlipping = isFlipped() != ((PermanentView) card).isFlipped();
            if (needsTapping || needsFlipping) {
                Animation.tapCardToggle(this, this, needsTapping, needsFlipping);
            }
            if (needsTapping && ((PermanentView) card).isTapped()) {
                AudioManager.playTapPermanent();
            }
            boolean needsTranforming = isTransformed() != card.isTransformed();
            if (needsTranforming) {
                Animation.transformCard(this, this, card.isTransformed());
            }
        }

        // Update panel attributes
        this.isChoosable = card.isChoosable();
        this.isSelected = card.isSelected();

        // Update art?
        boolean mustUpdateArt
                = (!getGameCard().getName().equals(card.getName()))
                || (getGameCard().isFaceDown() != card.isFaceDown());

        // Set the new card
        this.setGameCard(card);

        // Update tooltip text
        String cardType = getType(card);
        tooltipText.setText(getText(cardType, card));

        // Update the image
        if (mustUpdateArt) {
            updateArtImage();
        }

        // Update transform circle
        if (card.canTransform()) {
            BufferedImage transformIcon;
            if (isTransformed() || card.isTransformed()) {
                transformIcon = ImageManagerImpl.instance.getNightImage();
            } else {
                transformIcon = ImageManagerImpl.instance.getDayImage();
            }
            if (dayNightButton != null) {
                dayNightButton.setVisible(!isPermanent);
                dayNightButton.setIcon(new ImageIcon(transformIcon));
            }
        }
    }

    @Override
    public boolean contains(int x, int y) {
        return containsThis(x, y, true);
    }

    public boolean containsThis(int x, int y, boolean root) {
        Point component = getLocation();

        int cx = getCardX() - component.x;
        int cy = getCardY() - component.y;
        int cw = cardWidth;
        int ch = cardHeight;
        if (isTapped()) {
            cy = ch - cw + cx;
            ch = cw;
            cw = cardHeight;
        }

        return x >= cx && x <= cx + cw && y >= cy && y <= cy + ch;
    }

    @Override
    public CardView getOriginal() {
        return this.getGameCard();
    }

    @Override
    public void mouseClicked(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        if (getGameCard().hideInfo()) {
            return;
        }
        if (!tooltipShowing) {
            synchronized (this) {
                if (!tooltipShowing) {
                    TransferData transferData = getTransferDataForMouseEntered();
                    if (this.isShowing()) {
                        tooltipShowing = true;
                        callback.mouseEntered(e, transferData);
                    }
                }
            }
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        data.setComponent(this);
        callback.mouseDragged(e, data);
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        if (getGameCard().hideInfo()) {
            return;
        }
        data.setComponent(this);
        callback.mouseMoved(e, data);
    }

    @Override
    public void mouseExited(MouseEvent e) {
        if (getGameCard().hideInfo()) {
            return;
        }

        if (tooltipShowing) {
            synchronized (this) {
                if (tooltipShowing) {
                    tooltipShowing = false;
                    data.setComponent(this);
                    data.setCard(this.getGameCard());
                    data.setPopupText(tooltipText);
                    callback.mouseExited(e, data);
                }
            }
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        data.setComponent(this);
        data.setCard(this.getGameCard());
        data.setGameId(this.gameId);
        callback.mousePressed(e, data);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        callback.mouseReleased(e, data);
    }

    /**
     * Prepares data to be sent to action callback on client side.
     *
     * @return
     */
    private TransferData getTransferDataForMouseEntered() {
        data.setComponent(this);
        data.setCard(this.getGameCard());
        data.setPopupText(tooltipText);
        data.setGameId(this.gameId);
        data.setLocationOnScreen(data.getComponent().getLocationOnScreen()); // we need this for popup
        data.setPopupOffsetX(isTapped() ? cardHeight + cardXOffset + POPUP_X_GAP : cardWidth + cardXOffset + POPUP_X_GAP);
        data.setPopupOffsetY(40);
        return data;
    }

    protected final String getType(CardView card) {
        StringBuilder sbType = new StringBuilder();

        for (SuperType superType : card.getSuperTypes()) {
            sbType.append(superType.toString()).append(' ');
        }

        for (CardType cardType : card.getCardTypes()) {
            sbType.append(cardType.toString()).append(' ');
        }

        if (!card.getSubTypes().isEmpty()) {
            sbType.append("- ");
            for (SubType subType : card.getSubTypes()) {
                sbType.append(subType).append(' ');
            }
        }

        return sbType.toString().trim();
    }

    protected final String getText(String cardType, CardView card) {
        StringBuilder sb = new StringBuilder();
        if (card instanceof StackAbilityView || card instanceof AbilityView) {
            for (String rule : card.getRules()) {
                sb.append('\n').append(rule);
            }
        } else {
            sb.append(card.getName());
            if (!card.getManaCost().isEmpty()) {
                sb.append('\n').append(card.getManaCost());
            }
            sb.append('\n').append(cardType);
            if (card.getColor().hasColor()) {
                sb.append('\n').append(card.getColor().toString());
            }
            if (card.isCreature()) {
                sb.append('\n').append(card.getPower()).append('/').append(card.getToughness());
            } else if (card.isPlanesWalker()) {
                sb.append('\n').append(card.getLoyalty());
            }
            if (card.getRules() == null) {
                card.overrideRules(new ArrayList<>());
            }
            for (String rule : card.getRules()) {
                sb.append('\n').append(rule);
            }
            if (card.getExpansionSetCode() != null && !card.getExpansionSetCode().isEmpty()) {
                sb.append('\n').append(card.getCardNumber()).append(" - ");
                sb.append(card.getExpansionSetCode()).append(" - ");
                sb.append(card.getRarity().toString());
            }
        }
        return sb.toString();
    }

    @Override
    public void update(PermanentView card) {
        this.hasSickness = card.hasSummoningSickness();
        this.showCopySourceButton.setVisible(card.isCopy());
        update((CardView) card);
    }

    @Override
    public PermanentView getOriginalPermanent() {
        if (isPermanent) {
            return (PermanentView) this.getGameCard();
        }
        throw new IllegalStateException("Is not permanent.");
    }

    @Override
    public void updateCallback(ActionCallback callback, UUID gameId) {
        this.callback = callback;
        this.gameId = gameId;
    }

    public void setTransformed(boolean transformed) {
        this.transformed = transformed;
    }

    private void copySelections(CardView source, CardView dest) {
        if (source != null && dest != null) {
            dest.setSelected(source.isSelected());
            dest.setChoosable(source.isChoosable());
        }
    }

    @Override
    public void toggleTransformed() {
        this.transformed = !this.transformed;
        if (transformed) {
            // show night card
            if (dayNightButton != null) { // if transformbable card is copied, button can be null
                BufferedImage night = ImageManagerImpl.instance.getNightImage();
                dayNightButton.setIcon(new ImageIcon(night));
            }
            if (this.getGameCard().getSecondCardFace() == null) {
                LOGGER.error("no second side for card to transform!");
                return;
            }
            if (!isPermanent) { // use only for custom transformation (when pressing day-night button)
                copySelections(this.getGameCard(), this.getGameCard().getSecondCardFace());
                this.setTemporary(this.getGameCard());
                update(this.getGameCard().getSecondCardFace());
            }
        } else {
            // show day card
            if (dayNightButton != null) { // if transformbable card is copied, button can be null
                BufferedImage day = ImageManagerImpl.instance.getDayImage();
                dayNightButton.setIcon(new ImageIcon(day));
            }

            if (!isPermanent) { // use only for custom transformation (when pressing day-night button)
                copySelections(this.getGameCard().getSecondCardFace(), this.getGameCard());
                update(this.getTemporary());
                this.setTemporary(null);
            }
        }
        String temp = this.getGameCard().getAlternateName();
        this.getGameCard().setAlternateName(this.getGameCard().getOriginalName());
        this.getGameCard().setOriginalName(temp);
        updateArtImage();
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        if (getGameCard().hideInfo()) {
            return;
        }
        data.setComponent(this);
        callback.mouseWheelMoved(e, data);
    }

    public JPanel getCardArea() {
        return cardArea;
    }

    @Override
    public void componentResized(ComponentEvent ce) {
        doLayout();
        // this update removes the isChoosable mark from targetCardsInLibrary
        // so only done for permanents because it's needed to redraw counters in different size, if window size was changed
        // no perfect solution yet (maybe also other not wanted effects for PermanentView objects)
        if ((getUpdateCard() instanceof PermanentView)) {
            update(getUpdateCard());
        }
    }

    @Override
    public void componentMoved(ComponentEvent ce) {
    }

    @Override
    public void componentShown(ComponentEvent ce) {
    }

    @Override
    public void componentHidden(ComponentEvent ce) {
    }

    @Override
    public void setCardCaptionTopOffset(int yOffsetPercent) {
        yCardCaptionOffsetPercent = yOffsetPercent;
    }

    public int getCardCaptionTopOffset() {
        return yCardCaptionOffsetPercent;
    }

    @Override
    public JPopupMenu getPopupMenu() {
        return popupMenu;
    }

    @Override
    public void setPopupMenu(JPopupMenu popupMenu) {
        this.popupMenu = popupMenu;
    }

    public CardView getGameCard() {
        return gameCard;
    }

    public void setGameCard(CardView gameCard) {
        this.gameCard = gameCard;
    }

    public CardView getUpdateCard() {
        return updateCard;
    }

    public void setUpdateCard(CardView updateCard) {
        this.updateCard = updateCard;
    }

    public CardView getTemporary() {
        return temporary;
    }

    public void setTemporary(CardView temporary) {
        this.temporary = temporary;
    }

    public double getTappedAngle() {
        return tappedAngle;
    }

    public void setTappedAngle(double tappedAngle) {
        this.tappedAngle = tappedAngle;
    }

    public double getFlippedAngle() {
        return flippedAngle;
    }

    public void setFlippedAngle(double flippedAngle) {
        this.flippedAngle = flippedAngle;
    }
}
