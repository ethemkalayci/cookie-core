
package be.cookies.studio.core.service;

import be.cookies.studio.core.model.OfficialInstance;

import java.io.IOException;
import java.util.List;

public class OfficialInstanceServiceTest {

    public static void main(String[] args) {
        OfficialInstanceService service = new OfficialInstanceService();

        try {
            System.out.println("Chargement des instances officielles...");

            List<OfficialInstance> instances = service.loadOfficialInstances();

            System.out.println("Nombre d'instances : " + instances.size());

            for (OfficialInstance instance : instances) {
                System.out.println("--------------------------");
                System.out.println("Nom : " + instance.getName());
                System.out.println("ID : " + instance.getId());
                System.out.println("Minecraft : " + instance.getMinecraftVersion());
                System.out.println("Modloader : " + instance.getModLoader());
                System.out.println("Version du modpack : " + instance.getModpackVersion());
            }

            System.out.println("--------------------------");
            System.out.println("Test terminé avec succès.");

        } catch (IOException e) {
            System.err.println("Erreur de chargement : " + e.getMessage());
            e.printStackTrace();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Chargement interrompu.");
        }
    }
}
