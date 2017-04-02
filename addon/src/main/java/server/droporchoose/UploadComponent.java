package server.droporchoose;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.vaadin.event.dd.DragAndDropEvent;
import com.vaadin.event.dd.DropHandler;
import com.vaadin.event.dd.acceptcriteria.AcceptAll;
import com.vaadin.event.dd.acceptcriteria.AcceptCriterion;
import com.vaadin.server.StreamVariable;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.DragAndDropWrapper;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Html5File;
import com.vaadin.ui.Label;
import com.vaadin.ui.Panel;
import com.vaadin.ui.Upload;

/**
 * A combined Component for dropping files or choosing a file to upload
 * traditionally.
 * 
 * It provides a single interface to both components by setting callback
 * handlers. <br>
 * optional callbacks: <br>
 * {@link #setStartedCallback(UploadStartedHandler)}<br>
 * {@link #setProgressCallback(UploadProgressHandler)}<br>
 * {@link #setFailedCallback(UploadFailedHandler)}<br>
 * 
 * When a upload is finished you need to take care of deleting the temporary
 * file.
 * 
 * @author Sebastian Sindelar
 *
 */
public class UploadComponent extends DragAndDropWrapper {

	private static final long serialVersionUID = -4602066804316599960L;

	private final Label dropTextLabel = new Label("Drop file(s) here or");
	private final Upload upload = new Upload();
	private final HorizontalLayout panelContent = new HorizontalLayout(dropTextLabel, upload);

	private UploadReceivedHandler finishedCallback;
	private UploadStartedHandler startedCallback;
	private UploadProgressHandler progressCallback;
	private UploadFailedHandler failedCallback;

	private Path tempFolder = Paths.get(System.getProperty("java.io.tmpdir"));

	private Map<String, Path> uploadedFiles = Collections.synchronizedMap(new HashMap<>());

	// to know currently uploading file's name when choosing
	private String lastFileName;

	/**
	 * Create a new {@link UploadComponent}.
	 * 
	 * @param finishedCallback
	 *            {@link UploadReceivedHandler} called when upload is finished
	 */
	public UploadComponent(UploadReceivedHandler finishedCallback) {
		this();
		this.finishedCallback = finishedCallback;
	}

	/**
	 * Creates a new {@link UploadComponent}.
	 * 
	 * The {@link UploadReceivedHandler} must be set before the first file is
	 * uploaded.
	 */
	public UploadComponent() {
		super(new Panel());
		Panel panel = (Panel) getCompositionRoot();
		panel.addStyleName("doc-panel");
		panel.setSizeFull();
		panel.setContent(panelContent);

		dropTextLabel.setSizeUndefined();
		dropTextLabel.addStyleName("doc-drophint");

		upload.setImmediateMode(true);
		upload.setButtonCaption("choose a file");
		upload.setReceiver(this::receiveUpload);
		upload.addStartedListener(event -> uploadStarted(event.getFilename()));
		upload.addProgressListener((readBytes, contentLength) -> trackProgress(lastFileName, readBytes, contentLength));
		upload.addFinishedListener(event -> uploadFinished(event.getFilename()));
		upload.addFailedListener(event -> uploadFailed(event.getFilename()));
		upload.addStyleName("doc-upload");

		panelContent.setSpacing(true);
		panelContent.setSizeFull();
		panelContent.setComponentAlignment(dropTextLabel, Alignment.MIDDLE_RIGHT);
		panelContent.setComponentAlignment(upload, Alignment.MIDDLE_LEFT);
		panelContent.addStyleName("doc-layout");

		this.setDropHandler(new UploadDropHandler());
	}

	/**
	 * Called when a upload has started.
	 * 
	 * @param fileName
	 *            name of the received file
	 */
	protected void uploadStarted(String fileName) {
		lastFileName = fileName;
		if (startedCallback != null) {
			startedCallback.uploadStarted(fileName);
		}
	}

	/**
	 * Called on progress of an upload.
	 * 
	 * @param fileName
	 *            name of the received file
	 * @param readBytes
	 *            current uploaded bytes
	 * @param contentLength
	 *            file size
	 */
	protected void trackProgress(String fileName, long readBytes, long contentLength) {
		if (progressCallback != null) {
			progressCallback.uploadProgress(fileName, readBytes, contentLength);
		}
	}

	/**
	 * Called when a new file upload is received.
	 * 
	 * @param filename
	 *            name of the received file
	 * @param mimeType
	 *            file type of the received file
	 * @return A Outputstream the file can be written to.
	 */
	protected OutputStream receiveUpload(String filename, String mimeType) {
		Path filePath = tempFolder.resolve(generatedTempFileName());
		uploadedFiles.put(filename, filePath);
		return getOutputStream(filePath);
	}

	/**
	 * Called when a Upload has finished.
	 * 
	 * @param fileName
	 *            name of the received file
	 */
	protected void uploadFinished(String fileName) {
		if (finishedCallback == null) {
			// must be set before first use
			throw new IllegalStateException(
					"UploadReceivedHandler not set. Call setReceivedCallback() before first upload.");
		}
		Path filePath = uploadedFiles.get(fileName);
		finishedCallback.uploadReceived(fileName, filePath);
		uploadedFiles.remove(fileName);
	}

	/**
	 * Called when a Upload has failed.
	 * 
	 * @param fileName
	 *            name of the file where the upload failed
	 */
	protected void uploadFailed(String fileName) {
		if (failedCallback != null) {
			Path filePath = uploadedFiles.get(fileName);
			failedCallback.uploadFailed(fileName, filePath);
		}
		uploadedFiles.remove(fileName);
	}

	/**
	 * Builds the {@link OutputStream} for the uploaded file to be saved to.
	 * 
	 * This can by changed e.g. to a {@link ByteArrayOutputStream} to save it to
	 * memory. But than you need to take care of retrieving the file yourself
	 * after the upload.
	 * 
	 * @param filePath
	 *            where the file should be stored
	 * @return A Outputstream the file can be written to.
	 */
	protected OutputStream getOutputStream(Path filePath) {
		try {
			return Files.newOutputStream(filePath);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Generates to name of the temporary uploaded file.
	 * 
	 * @return filename that should be used the temporary file
	 */
	protected String generatedTempFileName() {
		return UUID.randomUUID().toString();
	}

	/**
	 * The folder the uploaded file will be stored.
	 * 
	 * Default: java.io.tmpdir
	 * 
	 * @return the folder where the uploads are stored
	 */
	public Path getTempFolder() {
		return tempFolder;
	}

	/**
	 * Sets the folder the temporary files are stored to.
	 * 
	 * Default: java.io.tmpdir
	 * 
	 * @param tempFolder
	 *            the folder where the upload should be stored
	 */
	public void setTempFolder(Path tempFolder) {
		this.tempFolder = tempFolder;
	}


	/**
	 * Sets the started callback that is called when a upload starts.
	 * 
	 * @param startedCallback
	 *            {@link UploadStartedHandler}
	 */
	public void setStartedCallback(UploadStartedHandler startedCallback) {
		this.startedCallback = startedCallback;
	}

	/**
	 * Sets the progress callback that is called when progress on an uploaded
	 * file is made.
	 * 
	 * @param progressCallback
	 *            {@link UploadProgressHandler}
	 */
	public void setProgressCallback(UploadProgressHandler progressCallback) {
		this.progressCallback = progressCallback;
	}

	/**
	 * Sets the received callback that is called when an upload finished
	 * successfully.
	 * 
	 * @param finishedCallback
	 *            {@link UploadReceivedHandler}
	 */
	public void setReceivedCallback(UploadReceivedHandler finishedCallback) {
		this.finishedCallback = finishedCallback;
	}

	/**
	 * Sets the failed callback that is called when a file upload fails.
	 * 
	 * @param failedCallback
	 *            {@link UploadFailedHandler}
	 */
	public void setFailedCallback(UploadFailedHandler failedCallback) {
		this.failedCallback = failedCallback;
	}

	/**
	 * The {@link Label} on the left that gives to hint for dropping files.
	 * 
	 * @return the {@link Label} with the drop hint on the left
	 */
	public Label getDropTextLabel() {
		return dropTextLabel;
	}

	/**
	 * The "normal" {@link Upload} component on the right.
	 * 
	 * @return the {@link Upload} component on the right.
	 */
	public Upload getChoose() {
		return upload;
	}

	/**
	 * The {@link HorizontalLayout} for positioning the components.
	 * 
	 * @return layout that contains the components
	 */
	public HorizontalLayout getLayout() {
		return panelContent;
	}

	/**
	 * Handler for finished files.
	 *
	 */
	@FunctionalInterface
	public interface UploadReceivedHandler {
		void uploadReceived(String fileName, Path filePath);
	}

	/**
	 * Handler for progress events.
	 * 
	 */
	@FunctionalInterface
	public interface UploadProgressHandler {
		void uploadProgress(String fileName, long readBytes, long contentLength);
	}

	/**
	 * Handler for started events.
	 * 
	 */
	@FunctionalInterface
	public interface UploadStartedHandler {
		void uploadStarted(String fileName);
	}

	/**
	 * Handler for failed upload events.
	 * 
	 */
	@FunctionalInterface
	public interface UploadFailedHandler {
		void uploadFailed(String fileName, Path filePath);
	}

	/**
	 * The {@link DropHandler} used for dropping files.
	 * 
	 */
	protected class UploadDropHandler implements DropHandler {
		private static final long serialVersionUID = 5637392002285104305L;

		@Override
		public AcceptCriterion getAcceptCriterion() {
			return AcceptAll.get();
		}

		@Override
		public void drop(DragAndDropEvent event) {
			WrapperTransferable transferable = (WrapperTransferable) event.getTransferable();
			Html5File[] files = transferable.getFiles();

			for (Html5File file : files) {
				OutputStream outputStream = receiveUpload(file.getFileName(), file.getType());

				StreamVariable streamVariable = new StreamVariable() {

					private static final long serialVersionUID = -7329119402679819252L;

					@Override
					public OutputStream getOutputStream() {
						return outputStream;
					}

					@Override
					public boolean listenProgress() {
						return progressCallback != null;
					}

					@Override
					public void onProgress(StreamingProgressEvent event) {
						trackProgress(event.getFileName(), event.getBytesReceived(), event.getContentLength());
					}

					@Override
					public void streamingStarted(StreamingStartEvent event) {
						uploadStarted(event.getFileName());
					}

					@Override
					public void streamingFinished(StreamingEndEvent event) {
						uploadFinished(event.getFileName());
					}

					@Override
					public void streamingFailed(StreamingErrorEvent event) {
						uploadFailed(event.getFileName());
					}

					@Override
					public boolean isInterrupted() {
						return false;
					}
				};
				file.setStreamVariable(streamVariable);
			}
		}
	}

}
