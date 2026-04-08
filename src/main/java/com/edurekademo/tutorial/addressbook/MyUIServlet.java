package com.edurekademo.tutorial.addressbook;

/*import javax.servlet.annotation.WebServlet;
import com.vaadin.server.VaadinServlet;
import com.vaadin.annotations.VaadinServletConfiguration;
*/
import jakarta.servlet.annotation.WebServlet;
import com.vaadin.server.VaadinServlet;
import com.vaadin.annotations.VaadinServletConfiguration;

@WebServlet(urlPatterns = "/*", name = "MyUIServlet", asyncSupported = true)
@VaadinServletConfiguration(ui = AddressbookUI.class, productionMode = false)
public class MyUIServlet extends VaadinServlet {
}
