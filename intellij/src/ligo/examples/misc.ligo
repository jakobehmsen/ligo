/*
These statement can be copied into a running Ligo environment (ligo.Main)
*/

// Define constructor named label
label => {
  x = 0
  y = 0
  string = ""
  foreColor = color("#FFF")
  backColor = color("#000")
  font = font("TimesRoman", "B", 12)

  setColor(backColor)
  fillRect(x, y, measureString(font, string).width, measureString(font, string).height)

  setFont(font)
  setColor(foreColor)
  drawString(string, x, y + measureString(font, string).ascent)
}

// Bind slot lbl to a construction of a label
lbl = label()

// Bind slot string of slot lbl to the singleton string "Hello World!"
lbl.string = "Hello World!"

/*Other slots of lbl can changed too, e.g. x and y*/