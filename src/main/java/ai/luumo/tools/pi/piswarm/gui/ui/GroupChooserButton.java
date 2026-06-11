package ai.luumo.tools.pi.piswarm.gui.ui;

import ai.luumo.tools.pi.piswarm.gui.model.AgentGroup;

import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.Font;
import java.awt.Insets;
import java.util.function.Consumer;

/**
 * A small square button showing a colour-coded {@link ChainIcon} for an agent's
 * (or board's) group. Clicking it opens a chooser listing all eight groups; the
 * supplied callback fires with the picked group.
 */
public final class GroupChooserButton extends JButton {

    private final ChainIcon icon;
    private AgentGroup group;
    private final Consumer<AgentGroup> onPick;

    public GroupChooserButton(AgentGroup initial, Consumer<AgentGroup> onPick) {
        this.group = initial == null ? AgentGroup.DEFAULT : initial;
        this.onPick = onPick;
        this.icon = new ChainIcon(16, this.group.color());
        setIcon(icon);
        setMargin(new Insets(2, 4, 2, 4));
        setFocusable(false);
        applyTooltip();
        addActionListener(e -> showMenu());
    }

    /** Update the displayed group colour (e.g. after a registry confirmation). */
    public void setGroup(AgentGroup g) {
        AgentGroup next = g == null ? AgentGroup.DEFAULT : g;
        if (next == group) {
            return;
        }
        this.group = next;
        icon.setColor(next.color());
        applyTooltip();
        repaint();
    }

    public AgentGroup getGroup() {
        return group;
    }

    private void applyTooltip() {
        setToolTipText("Group: " + group.label() + " — click to change");
    }

    private void showMenu() {
        JPopupMenu menu = new JPopupMenu();
        for (AgentGroup g : AgentGroup.all()) {
            JMenuItem item = new JMenuItem(g.label(), new ChainIcon(16, g.color()));
            if (g == group) {
                item.setFont(item.getFont().deriveFont(Font.BOLD));
            }
            item.addActionListener(ev -> onPick.accept(g));
            menu.add(item);
        }
        menu.show(this, 0, getHeight());
    }
}
