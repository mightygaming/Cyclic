package com.lothrazar.cyclicmagic.gui;
import java.util.ArrayList;
import java.util.List;
import com.lothrazar.cyclicmagic.ITooltipButton;
import net.minecraft.client.gui.GuiButton;

public abstract class GuiButtonTooltip extends GuiButton implements ITooltipButton {
  public GuiButtonTooltip(int buttonId, int x, int y, int widthIn, int heightIn, String buttonText) {
    super(buttonId, x, y, widthIn, heightIn, buttonText);
  }
  private List<String> tooltip = new ArrayList<String>();
  @Override
  public List<String> getTooltips() {
    return tooltip;
  }
  public void setTooltips(List<String> t) {
    tooltip = t;
  }
  public void setTooltip(final String t) {
    List<String> remake = new ArrayList<String>();
    remake.add(t);
    tooltip = remake;
  }
}
