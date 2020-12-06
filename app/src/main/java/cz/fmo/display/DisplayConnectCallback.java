package cz.fmo.display;


public interface DisplayConnectCallback {
    void onImageReceived(byte[] imageBytes, int imageWidth, int imageHeight);
    void onConnected();
    void onImageTransmissionStarted(int parts);
    void onImagePartReceived(int partNumber);
}
