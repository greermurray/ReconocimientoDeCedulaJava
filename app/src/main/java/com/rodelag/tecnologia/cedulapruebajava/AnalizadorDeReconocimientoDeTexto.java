package com.rodelag.tecnologia.cedulapruebajava;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AnalizadorDeReconocimientoDeTexto implements ImageAnalysis.Analyzer {

    private String cedulaDetectada = "";
    private final String cedulaObjetivo;
    private final CallbackDeReconocimientoDeTexto callback;
    private final ExecutorService executorService;
    private final Set<String> palabrasClaveDetectadasVieja = new HashSet<>();
    private final Set<String> palabrasClaveDetectadasNueva = new HashSet<>();

    private final Set<String> palabrasClaveCedulaVieja = new HashSet<>(Arrays.asList(
        "REPÚBLICA", "PANAMÁ", "TRIBUNAL", "ELECTORAL", "NOMBRE", "USUAL", "FECHA",
        "NACIMIENTO", "SEXO", "LUGAR", "EXPEDIDA", "TIPO", "SANGRE", "EXPIRA"
    ));

    private final Set<String> palabrasClaveCedulaNueva = new HashSet<>(Arrays.asList(
        "REPÚBLICA", "PANAMÁ", "DOCUMENTO", "IDENTIDAD", "NOMBRE", "USUAL", "FECHA",
        "NACIMIENTO", "SEXO", "LUGAR", "EXPEDIDA", "TIPO", "SANGRE", "EXPIRA"
    ));

    public AnalizadorDeReconocimientoDeTexto(String cedulaObjetivo, CallbackDeReconocimientoDeTexto callback) {
        this.cedulaObjetivo = cedulaObjetivo;
        this.callback = callback;
        this.executorService = Executors.newSingleThreadExecutor();
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    @Override
    public void analyze(@NonNull ImageProxy imagenProxy) {
        executorService.execute(() -> {
            InputImage imagen = InputImage.fromMediaImage(Objects.requireNonNull(imagenProxy.getImage()), imagenProxy.getImageInfo().getRotationDegrees());
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    .process(imagen)
                    .addOnSuccessListener(result -> {
                        procesamientoDeBloqueDeTexto(result);
                        imagenProxy.close();
                    })
                    .addOnFailureListener(e -> {
                        e.printStackTrace();
                        imagenProxy.close();
                    });
        });
    }

    private void procesamientoDeBloqueDeTexto(Text resultado) {
        for (Text.TextBlock bloque : resultado.getTextBlocks()) {
            for (Text.Line linea : bloque.getLines()) {
                procesamientoDeTexto(linea);
            }
        }
        if (coincidenciaDeDocumento()) {
            callback.alDetectarTexto(cedulaDetectada);
        }
    }

    private void procesamientoDeTexto(Text.Line linea) {
        for (Text.Element elemento : linea.getElements()) {
            verificarPalabrasClave(elemento.getText());
        }
    }

    private void verificarPalabrasClave(String texto) {
        if (palabrasClaveCedulaVieja.contains(texto)) {
            palabrasClaveDetectadasVieja.add(texto);
        }
        if (palabrasClaveCedulaNueva.contains(texto)) {
            palabrasClaveDetectadasNueva.add(texto);
        }
        if (texto.equals(cedulaObjetivo)) {
            cedulaDetectada = texto;
        }
    }

    private boolean coincidenciaDeDocumento() {
        return (palabrasClaveDetectadasVieja.containsAll(palabrasClaveCedulaVieja) || palabrasClaveDetectadasNueva.containsAll(palabrasClaveCedulaNueva)) && cedulaDetectada.equals(cedulaObjetivo);
    }

    public interface CallbackDeReconocimientoDeTexto {
        void alDetectarTexto(String cedula);
    }
}