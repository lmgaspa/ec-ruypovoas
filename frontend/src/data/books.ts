import { descriptions } from './descriptions';

export interface Book {
  id: string;
  title: string;
  imageUrl: string;
  price: string;
  description: string;
  author: string;
  additionalInfo: Record<string, string>;
  category: string;
  relatedBooks: { title: string; imageUrl: string; price: string; category: string; id: string }[];
  stock: number;
}

const selo = "Via Litterarum";
const idioma = "Português";

export const books: Book[] = [
  {
    id: "asombra",
    title: "A sombra no espelho: o secreto arquivo de enigmas – Edição especial",
    imageUrl: "/images/asombra.webp",
    price: "R$90,00",
    description: descriptions.asombra,
    author: "Ruy do Carmo Póvoas",
    additionalInfo: {
      Peso: "735 g",
      Dimensões: "22 × 15 × 3,8 cm",
      Selo: selo,
      ISBN: "978-65-86676-11-2",
      Edição: "1ª",
      "Ano de Publicação": "2020",
      "Nº de Páginas": "114",
      Idioma: idioma
    },
    category: "Romance",
    stock: 100,
    relatedBooks: [
      { id: "dizeres", title: "Dizeres esparsos: espaços de nós", imageUrl: "/images/dizeres.webp", price: "R$35,00", category: "Romance Espirita" },
      { id: "confessionario", title: "Confessionário", imageUrl: "/images/confessionario.webp", price: "R$40,00", category: "Poesia" }
    ]
  },      
  {
    id: "oratorio",
    title: "Oratório: santuário de antanho",
    imageUrl: "/images/oratorio.webp",
    price: "R$35,00",
    description: descriptions.oratorio,
    author:  "Ruy do Carmo Póvoas",
    additionalInfo: {
      Peso: "230 g",
      Dimensões: "22 × 15 × 1 cm",
      Selo: selo,
      ISBN: "978-65-86676-01-3",
      Edição: "1ª",
      "Ano de Publicação": "2020",
      "Nº de Páginas": "220",
      Idioma: idioma
    },
    category: "Poesia",
    stock: 100,
    relatedBooks: [
      { id: "asombra", title: "A sombra no espelho: o secreto arquivo de enigmas – Edição especial", imageUrl: "/images/asombra.webp", price: "R$90,00", category: "Romance" },
      { id: "dizeresdoavesso", title: "Dizeres do avesso", imageUrl: "/images/dizeresdoavesso.webp", price: "R$40,00", category: "Poesia" }
    ]
  },
  {
    id: "dizeres",
    title: "Dizeres esparsos: espaços de nós",
    imageUrl: "/images/dizeres.webp",
    price: "R$35,00",
    description: descriptions.dizeres,
    author:  "Ruy do Carmo Póvoas",
    additionalInfo: {
      Peso: "210 g",
      Dimensões: "22 × 15 × 1 cm",
      Selo: selo,
      ISBN: "978-8567303017",
      Edição: "1ª",
      "Ano de Publicação": "2014",
      "Nº de Páginas": "199",
      Idioma: idioma
    },
    category: "Poesia",
    stock: 100,
    relatedBooks: [
      { id: "oratorio", title: "Oratório: santuário de antanho", imageUrl: "/images/oratorio.webp", price: "R$35,00", category: "Poesia" },
      { id: "confessionario", title: "Confessionário", imageUrl: "/images/confessionario.webp", price: "R$40,00", category: "Poesia" }
    ]
  },
  {
    id: "materia",
    title: "Matéria acidentada",
    imageUrl: "/images/materia.webp",
    price: "R$25,00",
    description: descriptions.materia,
    author:  "Ruy do Carmo Póvoas",
    additionalInfo: {
      Peso: "290 g",
      Dimensões: "22 × 15 × 1,5 cm",
      Selo: selo,
      ISBN: "978-85-8151-053-8",
      Edição: "1ª",
      "Ano de Publicação": "2017",
      "Nº de Páginas": "152",
      Idioma: idioma
    },
    category: "Poesia",
    stock: 100,
    relatedBooks: [
      { id: "asombra", title: "A sombra no espelho: o secreto arquivo de enigmas – Edição especial", imageUrl: "/images/asombra.webp", price: "R$90,00", category: "Romance" },
      { id: "dizeres", title: "Dizeres esparsos: espaços de nós", imageUrl: "/images/dizeres.webp", price: "R$35,00", category: "Romance Espirita" }
    ]
  },
  {
    id: "confessionario",
    title: "Confessionário",
    imageUrl: "/images/confessionario.webp",
    price: "R$40,00",
    description: descriptions.confessionario,
    author:  "Ruy do Carmo Póvoas",
    additionalInfo: {
      Peso: "300 g",
      Dimensões: "15 × 22 × 3 cm",
      Selo: selo,
      ISBN: "978-65-86676-54-9",
      Edição: "1ª",
      "Ano de Publicação": "2022",
      "Nº de Páginas": "136",
      Idioma: idioma
    },
    category: "Poesia",
    stock: 100,
    relatedBooks: [
      { id: "materia", title: "Matéria acidentada", imageUrl: "/images/materia.webp", price: "R$25,00", category: "Poesia" },
      { id: "oratorio", title: "Oratório: santuário de antanho", imageUrl: "/images/oratorio.webp", price: "R$35,00", category: "Poesia" }
    ]
  },
  {
    id: "dizeresdoavesso",
    title: "Dizeres do avesso",
    imageUrl: "/images/dizeresdoavesso.webp",
    price: "R$40,00",
    description: descriptions.dizeresdoavesso,
    author:  "Ruy do Carmo Póvoas",
    additionalInfo: {
      Peso: "160 g",
      Dimensões: "15 × 22 × 1 cm",
      Selo: selo,
      ISBN: "978-65-86676-54-9",
      Edição: "1ª",
      "Ano de Publicação": "2023",
      "Nº de Páginas": "176",
      Idioma: idioma
    },
    category: "Poesia",
    stock: 100,
    relatedBooks: [
      { id: "materia", title: "Matéria acidentada", imageUrl: "/images/materia.webp", price: "R$25,00", category: "Poesia" },
      { id: "oratorio", title: "Oratório: santuário de antanho", imageUrl: "/images/oratorio.webp", price: "R$35,00", category: "Poesia" }
    ]
  },
];
