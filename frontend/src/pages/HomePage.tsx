import Container from '../components/common/Container';
import ContentBlock from '../components/common/ContentBlock';
import { useNavigate } from 'react-router-dom';
import { books } from '../data/books'; // ajuste o caminho se necessário

function HomePage() {
  const navigate = useNavigate();

  return (
    <Container>
      <ContentBlock 
        title="Ruy do Carmo Póvoas"
        imageUrl="/images/ruypovoas.webp"
        description="Ruy do Carmo Póvoas (1943) nasceu em Ilhéus. A partir de 1970, fixou residência em Itabuna, onde fundou o Ilê Axé Ijexá, terreiro de candomblé de origem nagô, de nação Ijexá, no qual exerce a função de babalorixá. É licenciado em Letras pela antiga Faculdade de Filosofia de Itabuna e Mestre em Letras Vernáculas (UFRJ). Lecionou Língua Portuguesa durante 50 anos, até quando se aposentou pela UESC. Coordenou, durante 16 anos, o Núcleo de Estudos Afro-Baianos Regionais – Kàwé, da Universidade Estadual de Santa Cruz, do qual é fundador. Também, sob sua coordenação, foram criados o Jornal Tàkàdá, o Caderno Kàwé e a Revista Kàwé. Poeta, contista e ensaísta, Ruy tem publicado: Vocabulário da paixão, A linguagem do candomblé, Itan dos mais-velhos, Itan de boca a ouvido, A fala do santo, VersoREverso, Da porteira para fora, A memória do feminino no candomblé, Mejigã e o contexto da escravidão, A viagem de Orixalá, Novos dizeres e Representações do escondido. Ocupa a cadeira 18 da Academia de Letras de Ilhéus e é membro fundador da Academia de Letras de Itabuna."
        isAuthor
      />

      <h2 className="text-4xl font-extrabold text-primary mb-16 text-center">Livros</h2>

      <div className="flex flex-wrap justify-center gap-16">
        {books.map((b) => (
          <div
            key={b.id}
            role="button"
            tabIndex={0}
            onClick={() => navigate(`/books/${b.id}`)}
            onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && navigate(`/books/${b.id}`)}
          >
            <ContentBlock
              title={b.title}
              imageUrl={b.imageUrl}
              description={b.title}
            />
          </div>
        ))}
      </div>
    </Container>
  );
}

export default HomePage;
