import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
@Component({
  standalone: true,
  imports: [RouterLink],
  template: `<main>
    <img src="brand/logo-cardenas.svg" alt="Clínica Dental Cárdenas">
    <p class="eyebrow">Proyecto académico · Sprint 1</p>
    <h1>Clínica Dental Cárdenas</h1>
    <p class="intro">Interfaz publicada para revisión. El servidor Java y la base de datos aún no están publicados.</p>
    <section><h2>Alcance de la revisión</h2><div class="cards">
      <article><span>HU01</span><h3>Usuarios y roles</h3><p>Revisión del acceso y la gestión de usuarios. Su funcionamiento completo requiere el servidor.</p></article>
      <article><span>HU02</span><h3>Registro de pacientes</h3><p>El código incluye validación de DNI. No se presentan registros de pacientes ni una ejecución clínica verificada.</p></article>
      <article><span>HU12</span><h3>Recuperación de contraseña</h3><p>La pantalla puede revisarse. El envío y la validación del código requieren el servidor y el proveedor de mensajería.</p></article>
    </div></section>
    <nav><a routerLink="/login">Revisar pantalla de acceso</a><a routerLink="/recuperar-contrasena">Revisar recuperación</a></nav>
    <p class="note">Esta publicación no acredita reuniones Scrum ni pruebas de extremo a extremo. Las siguientes versiones se publicarán al integrar los cambios en la rama principal.</p>
  </main>`,
  styles: [`:host{display:block;background:#f1f6f8;min-height:100vh;color:#193d52}main{max-width:1080px;margin:auto;padding:64px 24px}img{width:76px;height:76px}.eyebrow{text-transform:uppercase;letter-spacing:2px;color:#3979a5;font-size:12px;margin-top:28px}h1{font-size:clamp(34px,5vw,60px);line-height:1.1;max-width:760px;margin:16px 0}.intro{font-size:20px;line-height:1.6;max-width:740px;color:#516b7a}section{margin-top:48px}.cards{display:grid;grid-template-columns:repeat(3,1fr);gap:18px}article{padding:24px;background:white;border:1px solid #d9e4ea;border-radius:18px}article span{font-size:12px;font-weight:bold;color:#a95339}article p,.note{line-height:1.7;color:#516b7a}h3{font-size:19px}nav{display:flex;gap:16px;flex-wrap:wrap;margin:32px 0}a{background:#245c80;color:white;padding:14px 20px;border-radius:10px;text-decoration:none}.note{font-size:13px;max-width:850px}@media(max-width:700px){.cards{grid-template-columns:1fr}main{padding-top:32px}}`]
})
export class PagesPreviewComponent {}
