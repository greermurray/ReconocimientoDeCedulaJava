package com.rodelag.tecnologia.cedulapruebajava;

import android.util.Log;

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
            //INFO: Se llama al callback para indicar que se detectó la cédula
            callback.seEjecutaAlDetectarCedula(cedulaDetectada);
        }
    }

    private void procesamientoDeTexto(Text.Line linea) {
        for (Text.Element elemento : linea.getElements()) {
            verificarPalabrasClave(elemento.getText());
        }
    }

    private void verificarPalabrasClave(String texto) {
        agregarSiEsPalabraClave(texto);
        verificarCedula(texto);
    }

    private void agregarSiEsPalabraClave(String texto) {
        if (palabrasClaveCedulaVieja.contains(texto)) {
            palabrasClaveDetectadasVieja.add(texto);
        }
        if (palabrasClaveCedulaNueva.contains(texto)) {
            palabrasClaveDetectadasNueva.add(texto);
        }
    }

    private void verificarCedula(String texto) {
        if (esCedulaValida(texto)) {
            Log.e("PRUEBA-RODELAG", "POSIBLE CEDULA: " + texto);
            if (texto.equals(cedulaObjetivo)) {
                Log.e("PRUEBA-RODELAG", "CEDULA CORRECTA: " + texto);
                cedulaDetectada = texto;
            } else {
                limpiarPalabrasClaveDetectadas();
                //INFO: Se llama al callback para indicar que NO se detectó la cédula
                callback.seEjecutaAlNoDetectarCedula();
            }
        }
    }

    private boolean esCedulaValida(String texto) {
        //INFO: Posibles cédulas: 1-111-1111, 1-1111-1111, A-1111-1111, AA-111-1111
        return texto.matches("([A-Za-z]{2}-\\d{3}-\\d{4})|(\\d-\\d{3}-\\d{3})|(\\d-\\d{4}-\\d{4})|(\\w-\\d{4}-\\d{4})");
    }

    private void limpiarPalabrasClaveDetectadas() {
        //INFO: Si no es la cédula objetivo, se limpian las palabras clave detectadas
        palabrasClaveDetectadasNueva.clear();
        palabrasClaveDetectadasVieja.clear();
    }

    private boolean coincidenciaDeDocumento() {
        return (palabrasClaveDetectadasVieja.containsAll(palabrasClaveCedulaVieja) || palabrasClaveDetectadasNueva.containsAll(palabrasClaveCedulaNueva)) && cedulaDetectada.equals(cedulaObjetivo);
    }

    public interface CallbackDeReconocimientoDeTexto {
        void seEjecutaAlDetectarCedula(String cedula);
        void seEjecutaAlNoDetectarCedula();
    }
}