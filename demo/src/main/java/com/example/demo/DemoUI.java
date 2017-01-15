package com.example.demo;

import java.nio.file.Path;

import com.vaadin.annotations.Theme;
import com.vaadin.server.VaadinRequest;
import com.vaadin.ui.Notification;
import com.vaadin.ui.Notification.Type;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;

import server.droporchoose.UploadComponent;

@Theme("valo")
public class DemoUI extends UI{
	
	private static final long serialVersionUID = 6255201979868278965L;
	private final VerticalLayout mainLayout = new VerticalLayout();

	@Override
	protected void init(VaadinRequest request){

		UploadComponent uploadComponent = new UploadComponent();

		// not optional
		uploadComponent.setReceivedCallback(this::uploadReceived);

		// optional callbacks
		uploadComponent.setStartedCallback(this::uploadStarted);
		uploadComponent.setProgressCallback(this::uploadProgress);
		uploadComponent.setFailedCallback(this::uploadFailed);

		uploadComponent.setWidth(500, Unit.PIXELS);
		uploadComponent.setHeight(300, Unit.PIXELS);
		uploadComponent.setCaption("File upload");


		mainLayout.addComponent(uploadComponent);
		mainLayout.setMargin(true);
		setContent(mainLayout);
	}

	private void uploadReceived(String fileName, Path file) {
		Notification.show("Upload finished: " + fileName, Type.HUMANIZED_MESSAGE);
	}

	private void uploadStarted(String fileName) {
		Notification.show("Upload started: " + fileName, Type.HUMANIZED_MESSAGE);
	}

	private void uploadProgress(String fileName, long readBytes, long contentLength) {
		Notification.show(String.format("Progress: %s : %d/%d", fileName, readBytes, contentLength),
				Type.TRAY_NOTIFICATION);
	}

	private void uploadFailed(String fileName, Path file) {
		Notification.show("Upload failed: " + fileName, Type.ERROR_MESSAGE);
	}

}
