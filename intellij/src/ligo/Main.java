package ligo;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        MainFrame frame = new MainFrame();

        frame.setSize(1280, 960);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        frame.setVisible(true);
    }
}
