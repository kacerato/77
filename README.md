# Matte3D

Editor 3D Android nativo em Java + OpenGL ES 3.0, inspirado no fluxo de editores mobile como Prisma3D.

## v0.2

- UI redesenhada em cinza fosco, landscape e fullscreen
- viewport OpenGL ES 3.0
- Cube, Plane, Sphere e Cylinder
- seleção por toque diretamente no viewport
- duplo toque para focar o objeto selecionado
- modos NAV, MOVE, ROTATE e SCALE por gesto
- gizmo XYZ visual no objeto selecionado
- snap de movimento (0.25), rotação (15 graus) e escala
- hierarquia com visibilidade e bloqueio por objeto
- inspector com edição numérica precisa de Position / Rotation / Scale
- rename de objetos
- Undo / Redo com histórico de até 40 estados
- Duplicate / Delete / Reset Transform
- vistas Perspective, Front, Right e Top
- câmera orbital e pinch-to-zoom
- salvamento local da cena

## Build

O workflow em `.github/workflows/android.yml` gera um APK debug a cada push para `main`.

Requisitos locais: JDK 17, Android SDK 35 e Gradle compatível com o projeto.
