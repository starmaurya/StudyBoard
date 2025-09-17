📒 StudyBoard – Whiteboard App

StudyBoard is a simple whiteboard Android app built with Kotlin.
It allows drawing strokes, adding text, shapes, undo/redo, and saving as PNG or JSON.

🚀 Features

✏️ Draw freehand strokes
⬛ Add shapes (square for now)
🔤 Add text at desired position
↩️ Undo / Redo support
🖼 Save board as PNG in Pictures/StudyBoard
📑 Export JSON for restoring state later
🧽 Eraser tool to remove mistakes
📲 Adaptive MVVM architecture with repository pattern

🔧 Setup

Clone the repo:

git clone https://github.com/starmaurya/StudyBoard.git

📂 Project Structure
com.starmaurya.whiteboard
│
├── models/                 # Serializable models (Stroke, Shape, Text, Payload)
├── repository/             # FileRepository + MediaStoreFileRepository
├── viewmodels/             # WhiteboardViewModel
├── views/                  # WhiteboardView (custom drawing view)
├── ui/                     # WhiteboardFragment (UI + toolbar)
├── utils/                  # AppLogger (custom logger)
└── ...

📌 Roadmap
More shapes (circle, arrow)
Brush styles (marker, highlighter)
Import JSON → restore board state
Multi-page whiteboard
Collaborative whiteboard (network sync)

🛡 License
MIT License © 2025 [Star Maurya]